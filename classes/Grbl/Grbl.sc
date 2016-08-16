Grbl : Arduino
{
	classvar <>numInstances = 0;

	var <id, <>stateAction, <rxBuf, <rxBufsize;
	var <>mPos, <>wPos, <>pBuf, <>mode, <stateRoutine;
	var <>postState = false;
	var <posBus, <writePos = false, <>stateUpdateRate;
	var <>server;
	var <posPlotter;
	var <pathHistory;

	*parserClass { ^GrblParser }

	// NOTE: overwrites Arduino's init method
	init {
		// used to create a unique ID, no need to remove when freed
		Grbl.numInstances = Grbl.numInstances +1;
		id = Grbl.numInstances;

		// NOTE: rxBuf stored as a list of numbers, each being the
		// size of the string of each message.  This allows for keepng track
		// of the total characters in the rx buffer, most importantly for removing
		// values when notified that a message was processed.
		rxBuf = List();
		rxBufsize = 0;

		parser = this.class.parserClass.new(this);
		mPos = [0,0,0];
		wPos	 = [0,0,0];
		pBuf = 0;

		// default rate that state will be requested when stateRoutine runs
		stateUpdateRate = 8;

		// // default response to a state message
		// stateAction = { |state, mpos, wpos|
		// 	format(
		// 		"% mode\tmachine position:\t%\n\t\t\tworld position:\t\t%\n",
		// 		state, mpos, wpos
		// 	).postln;
		// };

		inputThread = fork { parser.parse };
	}

	// optionally initialize the pathHistory, which keeps a running
	// buffer of the last maxSize values, for plotting, etc.
	initPathHistory { |minDist=0.1, minPeriod=0.25, startNow=false, maxSize|
		pathHistory = HistoryList(minDist: minDist, minPeriod: minPeriod, startNow: startNow);
		maxSize !? {pathHistory.maxSize_(maxSize)};
	}

	// Note: .send increments the rxBuf with the message character size
	// use port.putall to bypass the rxBuf
	// more on G-Code commands: http://reprap.org/wiki/G-code
	send { | aString |
		var str;
		str = aString ++ "\n";
		port.putAll(str);

		// add this message's size to the stream buffer
		rxBuf.add(str.size); // .size takes the ascii size? "\n".size == 1
		rxBufsize = rxBuf.sum;

		//GUI update
		// this.changed(\rxSize, rxBuf.size);
		this.changed(\rxSum, rxBufsize);
	}

	// 'ok' received by parser, remove that msg's char count from rxBuf
	rmvOldestMsg {
		if( rxBuf.size > 0, {
			rxBuf.removeAt(0);
			rxBufsize = rxBuf.sum;
			// rxBuf.postln; // debug
			// rxBufsize.postln;  // debug
			this.changed(\rxSum, rxBufsize);
		});
	}

	clearRxBuf {
		rxBuf.clear;
		rxBufsize = 0;
		this.changed(\rxSum, rxBufsize);
	}

	// NOTE: no bound/feed checking at this point, that must happen
	// prior to calling goTo_
	goTo_ { |toX, toY, feed|
		var cmd = "G01";
		toX !? {cmd = cmd ++ "X" ++ toX};
		toY !? {cmd = cmd ++ "Y" ++ toY};
		feed !? {cmd = cmd ++ "F" ++ feed};
		postf("sending: %, %, %\n", toX, toY, feed);
		this.send(cmd);
	}

	// go to x pos
	x_ { |toX, feed|
		var cmd = "G01";
		toX !? {cmd = cmd ++ "X" ++ toX};
		feed !? {cmd = cmd ++ "F" ++ feed};
		this.send(cmd);
	}

	// go to y pos
	y_ { |toY, feed|
		var cmd = "G01";
		toY !? {cmd = cmd ++ "Y" ++ toY};
		feed !? {cmd = cmd ++ "F" ++ feed};
		this.send(cmd);
	}

		// go to y pos
	z_ { |toY, feed|
		var cmd = "G01";
		toY !? {cmd = cmd ++ "Z" ++ toY};
		feed !? {cmd = cmd ++ "F" ++ feed};
		this.send(cmd);
	}

	feed_ { |feedRate|
		feedRate !? {this.send("G0F" ++ feedRate.asString)}
	}

	// Go to a postion over specific duration
	// NOTE: doesn't currently account for acceleration, so faster moves
	// will actually take a bit longer than expected
	goToDur_ { |toX, toY, duration|
		var dist, feedRateSec, feed;

		dist = Point(toX, toY).dist(Point(wPos[0], wPos[1]));
		feedRateSec = dist / duration; // dist/sec
		feed = (feedRateSec * 60);

		this.goTo_( toX, toY, feed );
	}
	// goToDur_ { |toX, toY, duration|
	// 	var distX, distY, maxDist, feedRateSec, feed;
	//
	// 	distX = (toX - wPos[0]).abs;
	// 	distY = (toY - wPos[1]).abs;
	// 	maxDist = max(distX, distY);
	// 	feedRateSec = maxDist / duration; // dist/sec
	// 	feed = (feedRateSec * 60); //dist/min
	//
	// 	this.goTo_( toX, toY, feed ); // feedRateEnv.at(feedRate.clip(1, 40))
	// }


	// NOTE: ? status, ~ cycle start/resume, ! feed hold, and ^X soft-reset
	// are responded to immediately and so do not need to be tracked in the rxBuf
	// so use port.putAll (.send adds to the rxBuf)
	state		{ port.putAll("?") }
	pause	{ port.putAll("!") }
	resume	{ port.putAll("~") }
	reset		{
		port.putAll([24, 120]);  // cmd + x - no CR needed
		this.clearRxBuf;
	}

	home	{ port.putAll("$H\n") }
	unlock	{
		if( mode == "Alarm",
			{ port.putAll("$X\n") },
			{
				mode.isNil.if({
					"GRBL state isn't known, first query .state before unlocking".warn;
				},{
					var msg;
					msg = format("GRBL is in % mode, no need to unlock", mode);
					this.changed(\status, msg);
					msg.postln;
					// clear the message in 3 seconds
					SystemClock.sched(3, {this.changed(\status, ""); nil});
				});
			}
		);
	}

	// getters
	commands	{ this.send("$") }
	settings	{ this.send("$$") }
	gCodeParams	{ this.send("$#") }

	// setters
	invertAxes_ {|invXbool, invYbool|
		var id;
		id = case
		{invXbool and: invYbool}			{3}
		{invXbool and: invYbool.not}		{1}
		{invYbool and: invXbool.not}		{2}
		{invXbool.not and: invYbool.not}	{0};

		this.send("$3="++id)
	}

	worldOffset_ { |x, y|
		var str;
		str = format("G10L2P0X%Y%", x, y);
		this.send(str)
	}

	maxTravelX_ { |distance|
		this.send("$130="++distance)
	}
	maxTravelY_ { |distance|
		this.send("$131="++distance)
	}
	maxTravelZ_ { |distance|
		this.send("$132="++distance)
	}

	stepPerMmX_ { |steps|
		this.send("$100="++steps)
	}
	stepPerMmY_ { |steps|
		this.send("$101="++steps)
	}
	stepPerMmZ_ { |steps|
		this.send("$102="++steps)
	}

	maxRateX_ { |mmPerMin|
		this.send("$110="++mmPerMin)
	}
	maxRateY_ { |mmPerMin|
		this.send("$111="++mmPerMin)
	}
	maxRateZ_ { |mmPerMin|
		this.send("$112="++mmPerMin)
	}

	maxAccelX_ { |mmPerSec2|
		this.send("$120="++mmPerSec2)
	}
	maxAccelY_ { |mmPerSec2|
		this.send("$121="++mmPerSec2)
	}
	maxAccelZ_ { |mmPerSec2|
		this.send("$122="++mmPerSec2)
	}

	// bool, 1 or 0	// set soft limit on/off
	enableSoftLimit_ { |bool|
		bool.asBoolean.if(
			{ this.send("$20=1") },
			{ this.send("$20=0") }
		)
	}

	// bool, 1 or 0	// set hard limit on/off
	enableHardLimit_ { |bool|
		bool.asBoolean.if(
			{ this.send("$21=1") },
			{ this.send("$21=0") }
		)
	}

	/*		HOMING		*/
	// bool, 1 or 0	// set hard limit on/off
	enableHoming_ { |bool|
		bool.asBoolean.if(
			{ this.send("$22=1") },
			{ this.send("$22=0") }
		)
	}

	homingInvertDirection_ {|invXbool, invYbool|
		var id;
		id = case
		{invXbool and: invYbool}			{3}
		{invXbool and: invYbool.not}		{1}
		{invYbool and: invXbool.not}		{2}
		{invXbool.not and: invYbool.not}	{0};

		this.send("$23="++id)
	}

	homingFeed_ { |mmPerMin|
		this.send("$24="++mmPerMin)
	}

	homingSeek_ { |mmPerMin|
		this.send("$25="++mmPerMin)
	}

	homingDebounce_ { |mSec|
		this.send("$26="++mSec)
	}

	homingPullOff_ { |mm|
		this.send("$27="++mm)
	}

	updateState_ { |bool = true, updateRate, postStateBool|

		stateRoutine	!?	 { stateRoutine.stop };
		postStateBool !? { postState = postStateBool };
		updateRate !?	 { stateUpdateRate = updateRate};

		bool.if{
			stateRoutine.notNil.if(
				{ stateRoutine.isPlaying.not.if{ stateRoutine.reset.play } },
				{ stateRoutine = Routine.run({
					inf.do {
						this.state;
						stateUpdateRate.reciprocal.wait
					}
					})
				}
			);
		};
	}

	plotMotorPositions_ { |bool, boundLo = -90, boundHi = 90, plotLength = 50, plotRefreshRate = 10, plotMode = \points |
		bool.if(
			{
				{	var cond;
					cond = Condition(false);
					this.writePosToBus_(true, completeCondition: cond);
					cond.wait;

					posPlotter = ControlPlotter(posBus.bus, 2, plotLength, plotRefreshRate, plotMode);
					posPlotter.start.bounds_(boundLo,boundHi);
				}.fork(AppClock);
			},{
				posPlotter !? {posPlotter.free};
			}
		)
	}

	// write the motor position to a bus so it can be plotted
	writePosToBus_ { |bool = true, busnum, completeCondition|

		if (bool) {
			server ?? {server = Server.default};
			server.waitForBoot({

				//make sure the state is being updated internally (setting, state and w/mPos)
				stateRoutine.isPlaying.not.if { this.updateState_(true) };

				posBus ?? {
					posBus =  busnum.notNil.if(
						{ CtkControl.play(2, bus: busnum) },
						{ CtkControl.play(2) }
					);
				};
				completeCondition !? {completeCondition.test_(true).signal};
			});
		} {
			posBus !? { posBus.free; posBus = nil };
		};

		// set the writePos variable so the parser whether to update
		// posBus with the position value
		writePos = bool;
	}

	free {
		stateRoutine.stop;
		posPlotter !? {posPlotter.free};
		posBus !? {posBus.free; posBus = nil};
		pathHistory !? {pathHistory = nil};
		this.close;
	}
}


GrblParser {

	var <grbl, <port;
	var asciiInputLine, <charState;

	*new { | aGrbl |
		^super.newCopyArgs(aGrbl, aGrbl.port).init
	}

	init { }

	parse {
		// hold each line of feedback from GRBL
		asciiInputLine = List();
		charState = nil;

		// start the loop that reads the SerialPort
		loop { this.parseByte(port.read) };
	}

	parseByte { | byte |
		if (byte === 13) {
			// wait for LF
			charState = 13;
		} {
			if (byte === 10) {
				if (charState === 13) {
					// CR/LF encountered, wrap up this line

					if (asciiInputLine.notEmpty) {
						//postf("asciiInputLine: %\n", asciiInputLine); // debug
						this.chooseDispatch(asciiInputLine);
					};

					// clear the line stream
					asciiInputLine.clear;
					charState = nil;
				}
			} {
				asciiInputLine.add( byte );
			}
		}
	}


	// note: the dispatching variable denotes if a message from
	// GRBL is complete signed with [ok]
	chooseDispatch { |asciiLine|
		var localCopy;

		// must copy line so it isn't lost in the fork below once
		// asciiInputLine.clear in parseByte()
		localCopy = List.copyInstance(asciiLine);

		block { |break|

			// "ok"
			if ( asciiLine.asArray == [111, 107] ) {

				// // update running count of the serial rx buffer's size
				// if( grbl.rxBuf.size > 0, {
					// grbl.rxBuf.removeAt(0);
					// grbl.changed(\rxSize, grbl.rxBuf.size);
					// grbl.changed(\rxSum, grbl.rxBuf.sum);
				// } );
				grbl.rmvOldestMsg;
				// this.postGRBLinfo(asciiLine); // uncomment to post 'ok'
				break.();
			};

			switch( localCopy[0],

				// "<" - GRBL motor state response
				60, {
					fork{
						var stateInfo;
						stateInfo = this.parseMotorState(localCopy);
						// execute user-specified stateAction method with the stateInfo
						grbl.stateAction.value( *stateInfo );
						grbl.postState.if { stateInfo.postln };
					};
					break.();
				},

				// "$" - GRBL params/settings
				36, {
					this.postGRBLinfo(asciiLine);
					// could further filter asciiLine[1]
					// for separate dispatch actions/parsing
					// "N" - view startup blocks - $N
					// "$" - view GRBL settings - $$
					break.();
				},

				// "[" - G-code info
				91, {
					grbl.state; // query state to set mode/coordinate variables
					this.postGRBLinfo(asciiLine);
					// "#" - view G-code parameters - $#
					// "G" - view G-code parser state - $G
					grbl.changed(\status, asciiLine.asAscii);
					break.();
				},

				// "G" - Grbl 0.9g ['$' for help]
				71, {
					if (localCopy[0..3] == List[ 71, 114, 98, 108 ], //"Grbl"
						// Startup / reset
						{
							// query state to set mode/coordinate variables
							grbl.state;
							// "Welcome to Grbl in SC".postln; // debug
						}
					);
				},

				// "e" - error
				101, {
					grbl.changed(\error, asciiLine.asAscii);
					this.postGRBLinfo(asciiLine);
				},

				// "A" - Alarm
				65, {
					grbl.changed(\error, asciiLine.asAscii);
					grbl.changed(\mode, "Alarm");
					grbl.mode = "Alarm";
					this.postGRBLinfo(asciiLine);
				},
			);

			// catchall - just post the message
			this.postGRBLinfo(asciiLine);
		}
	}


	postGRBLinfo { | asciiLine |
		// ... do more if desired for generic messages
		asciiLine.asAscii.postln;
	}


	/*
	---Parse the message from GRBL---
	asciiLine.asAscii returns line in the format:
	<Idle,MPos:5.529,0.560,7.000,WPos:1.529,-5.440,-0.000>
	<Alarm,MPos:0.000,0.000,0.000,WPos:150.000,84.000,0.000,Buf:0>
	*/
	parseMotorState { | asciiLine |
		var split, mode, mPos, wPos, pBuf;

		//debug
		// asciiLine.asAscii.postln;


		split = asciiLine.asAscii.split($,);
		mode = split[0].drop(1);
		mPos = split[1..3].put(0, split[1].drop(5)).asFloat;
		wPos = split[4..6].put(0, split[4].drop(5)).asFloat;
		pBuf = split[7].drop(4).asInt;

		// grbl.postState.if{ postf("pBuf  %\n", pBuf) }; // debug

		// store the mode/pos back to the arduionoGRBL state vars
		grbl.wPos = wPos;
		grbl.mPos = mPos;
		grbl.pBuf = pBuf;

		// check former versus new mode state
		if( grbl.mode != mode, {
			grbl.changed(\mode, mode);
		});
		grbl.mode = mode;

		grbl.changed(\wPos, wPos);
		grbl.changed(\mPos, mPos);
		grbl.changed(\pBuf, pBuf);

		grbl.writePos.if { grbl.posBus.set(wPos[0..1]) };
		grbl.pathHistory !? {grbl.pathHistory.add(*wPos[0..1])};

		^[mode, wPos, mPos, pBuf]
	}
}