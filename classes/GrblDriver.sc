/* A class to drive a Grbl instance automatically */

// Dependencies:
// 	CTK (followSynthXY)
//		ControlPlotter (plotMotorPositions)

// TODO: followSynthXY functionality could probably be replaced by bus.getSyncronous
// TODO: remove dependency on CTK - CTKControl, etc
// TODO: work out relationship between motorInstructionRate and updateStateHz
// TODO: inspect for world position offset and max travel params to calc soft limits in SC
// TODO: many of these params are machine-specific, subclass for each machine, or be able to read in settings file

GrblDriver : Grbl {
	var <grbl; // copyArgs

	var <>maxDistPerSec	= 90; 		// e.g. 180 / 3.2702541628;	// seconds to go 180 degrees
	var <>maxLagDist		= 10; 		// max units that the world can lag behind a control signal
	var <minFeed				= 50;
	var <maxFeed				= 4675;
	var <>underDrive		= 1.0;
	var <>overDrive			= 1.1;
	var <>dropLag 			= 0.7;		// if a move is skipped, shorten the next one so it doesn't have to make up the full distance
	var <motorInstructionRate = 45; // rate to send new motor destinations
	var <>minMoveQueue	= 2;			// min moves queue'd up in buffer

	var <> catchSoftLimit	= true;		// check location before sending to GRBL to catch before soft limit to prevent ALARM
	var <softClipMargin 	= 0.5;		// offset from soft limits to clip before GRBL soft limit alarm

	var <xLimitLow, <xLimitHigh, <yLimitLow, <yLimitHigh;
	var <xClipLow, <xClipHigh, <yClipLow, <yClipHigh;

	var <followSynthXY, <followDefXY, <followResponderXY;
	var <starving = false, <lagging = false;
	var <driveView, <feedSpec, rx_buf_size = 128;
	// var <>debug;

	*new { |aGrbl|
		^super.newCopyArgs(aGrbl).init;
	}

	init {
		feedSpec = ControlSpec(minFeed, maxFeed, default: 500);

		// TODO: move these bounds to motor driver class, where they must be explicitly set,
		// or infer them from soft limit state in GRBL
		// These defaults are for mtm's pan/tilt rig
		xLimitLow	=	292.00.half.neg;
		xLimitHigh	=	292.00.half;
		yLimitLow	=	164.00.half.neg;
		yLimitHigh	=	164.00.half;

		xClipLow =	xLimitLow	 + 0.5;
		xClipHigh =	xLimitHigh - 0.5;
		yClipLow =	yLimitLow + 0.5;
		yClipHigh =	yLimitHigh - 0.5;
	}

	gui { driveView = GrblView(grbl) }

	// Grbl recommends not to exceed 5Hz update rate, but....
	followSerialBufXY_ { |ctlBusX, ctlBusY, driveRate, updateStateHz, gui = false|
		var lastDest;

		"initializing serial bus following".postln;

		ctlBusX ?? {"Must provide an X control bus to follow".error; ^this};
		ctlBusY ?? {"Must provide a Y control bus to follow".error; ^this};

		// write the position of the motor to a bus
		// not necessary?? - this is used for plotting
		// writePos.not.if{ this.writePosToBus_(true) };

		// monitor how full the serial buffer is: starved or dropping messages
		gui.if{ driveView = GrblView(grbl) };

		// start the stream buffer fresh
		streamBuf.clear;

		// make sure stateRoutine is playing to update wPos and state variables
		stateRoutine.isPlaying.not.if{
			updateState_(true, updateStateHz);
		};

		// ensure lastDest has a value
		lastDest	?? {
			postf("setting last dest as current wPos: %\n", wPos[0..1].asPoint);
			lastDest = wPos[0..1].asPoint;
		};
		// forward driveRate to motorInstructionRate, which is used for waittime below
		driveRate	!? { motorInstructionRate = driveRate };

		// start the synth that forwards the driving control signal to the lang
		server ?? {server = Server.default};
		server.waitForBoot({

			followSynthXY.isNil.if(
				{
					var followDef;
					followDefXY = CtkSynthDef( \busFollowXY++id, {// make the def unique w/ id
						| followBusX, followBusY, sendRate|
						SendReply.ar(
							Impulse.ar(sendRate),
							'/busFollowXY'++id,
							[In.kr(followBusX, 1), In.kr(followBusY, 1)]
						)
					});
					server.sync;

					followSynthXY = followDefXY.note
					.followBusX_(ctlBusX)
					.followBusY_(ctlBusY)
					.sendRate_(motorInstructionRate)
					.play
				},{
					followSynthXY
					.followBusX_(ctlBusX)
					.followBusY_(ctlBusY)
					.sendRate_(motorInstructionRate)
					.run;
				}
			);
		});

		// create a responder to receive the driving control signal from the server (followSynthXY)
		// TODO: could likey be replace by a routine calling ctlBusX/ctlBusY.getSyncronous
		followResponderXY ?? {

			followResponderXY = OSCFunc({ |msg, time, addr, recvPort|
				var goTo, maxNextDist, sent, catchup = 1;
				var deltaFromWorld, deltaFromLast;
				var nextPos, nextFeed;
				var droppedPrev = false;

				if (mode != "Alarm") {
					// the target destination from the control sig
					goTo = msg[3..4].asPoint;

					maxNextDist	= maxDistPerSec / motorInstructionRate;

					// distance from...
					deltaFromWorld = goTo.dist(wPos[0..1].asPoint);	// the latest world position
					deltaFromLast = goTo.dist(lastDest);					// the last scheduled destination

					// is the instruction buffer starving ?
					starving = (streamBuf.size < minMoveQueue);
					this.changed(\starving, starving);

					// is it lagging too far behind the world?
					lagging = (deltaFromWorld > maxLagDist);
					this.changed(\lagging, lagging);

					// is it trying to go farther than a maximum single drive instruction?
					if (deltaFromLast > maxNextDist) {
						// limit the next instruction to a max rate and distance
						var difRatio;
						difRatio = maxNextDist / deltaFromLast;

						nextPos = lastDest + (goTo - lastDest * difRatio);
						nextFeed = maxFeed;
						// debug.if{"Exceeding max move - limiting next destination".postln};
					} {
						if (droppedPrev)
						{	// don't go as far because the last move was skipped
							nextPos = lastDest + (goTo - lastDest * dropLag);
						}{	// a regular destination and feed
							nextPos = goTo;
						};

						nextFeed = (feedSpec.map(deltaFromLast/maxNextDist) * overDrive).clip(minFeed,maxFeed);

						// nextFeed = nextFeed * (1+(overDrive * (deltaFromLast/maxNextDist)));

						// slow down if the instruction list is starving
						if( starving, {
							nextFeed = nextFeed * underDrive;
							// debug.if{"STARVING - under driving the feed".postln};
						}
						);

						// if( lagging,
						// 	{	// instruct to move further, bump up the next feed rate
						// 		// clipping at max feed
						// 		nextFeed = [
						// 			(nextFeed * 1.2),
						// 			maxFeed
						// 		].minItem;
						// 	},
						// 	{	// slow down if the instruction list is starving
						// 		if( starving, {
						// 			nextFeed = nextFeed * underDrive;
						// 			debug.if{"STARVING - under driving the feed".postln};
						// 			} //, { catchup = 1 }
						// 		);
						// 	}
						// );
					};

					// try to send the move, returns false if no room in buffer
					sent = this.submitMoveXY(
						nextPos.x.round(0.001),
						nextPos.y.round(0.001),
						nextFeed.round(0.001)
					);

					if (sent)
					{
						lastDest = nextPos;
						this.changed(\sent, 1);
						droppedPrev = false;
					} {
						// debug.if{ "skipping move".postln };
						this.changed(\sent, 0);
						droppedPrev = true;
					};
				}
				{ "won't follow in ALARM mode".postln };
			},
			'/busFollowXY'++id // the OSC path
			);
		};
	}

	// used by followSerialBufXY_
	submitMoveXY { |toX, toY, feed|
		var cmd, size;

		cmd = "G01";

		if( catchSoftLimit, {
			toX	!? { cmd = cmd ++ "X" ++ this.checkBoundX(toX) };
			toY	!? { cmd = cmd ++ "Y" ++ this.checkBoundY(toY) };
		},{
			toX	!? { cmd = cmd ++ "X" ++ toX };
			toY	!? { cmd = cmd ++ "Y" ++ toY };
		});

		feed !? {cmd = cmd ++ "F" ++ feed};

		size = cmd.size + 1; // add 1 for the eol char added in .send

		// only send the move if the message won't overflow the serial buffer
		if ( (size + streamBuf.sum) < rx_buf_size,
			{ this.send(cmd); ^true },{^false}
		);
	}

	// Catch the move request before soft limit in GRBL to avoid ALARM
	checkBoundX { |toX|
		^if (toX.inRange(xClipLow, xClipHigh)) {
			toX
		} {
			warn("clipping the requested X position!");
			toX.clip(xClipLow, xClipHigh);
		};
	}

	checkBoundY { |toY|
		^if (toY.inRange(yClipLow, yClipHigh)) {
			toY
		} {
			warn("clipping the requested Y position!");
			toY.clip(yClipLow, yClipHigh);
		};
	}

	// used by follow
	motorInstructionRate_ { |newRate|
		newRate !? {
			motorInstructionRate = newRate;
			followSynthXY !? {followSynthXY.sendRate_(motorInstructionRate)};
		}
	}

	minFeed_ { |feedRate|
		minFeed = feedRate;
		feedSpec.minval_(minFeed);
		feedSpec.default_(minFeed + (maxFeed-minFeed).half);
	}

	maxFeed_ { |feedRate|
		maxFeed = feedRate;
		feedSpec.maxval_(maxFeed);
		feedSpec.default_(minFeed + (maxFeed-minFeed).half);
	}


	unfollow {
		// need to nil this so it's recreated when follow is called
		// or else lastDest is not updated with the new value for some reason ???
		followResponderXY !? {
			followResponderXY.free;
			followResponderXY = nil;
		};
		followSynthXY !? {followSynthXY.pause};
		driveView !? {driveView.free};
	}

		// To set bounds for both plotters and for catching moves that would
	// otherwise trigger soft limit ALARMs in GRBL
	// see catchSoftLimit arg in followSerialBufXY_ and submitMoveXY
	xLimitLow_ { |lowBound|
		lowBound !? { xLimitLow = lowBound; this.updateSoftClipLimits }
	}
	xLimitHigh_ { |highBound|
		highBound !? { xLimitHigh = highBound; this.updateSoftClipLimits }
	}
	yLimitLow_ { |lowBound|
		lowBound !? { yLimitLow = lowBound; this.updateSoftClipLimits }
	}
	yLimitHigh_ { |highBound|
		highBound !? { yLimitHigh = highBound; this.updateSoftClipLimits }
	}

	softLimitsSC_ { |xLow,xHigh,yLow,yHigh|
		this.xLimitLow_(xLow); this.xLimitHigh_(xHigh);
		this.yLimitLow_(yLow); this.xLimitHigh_(yHigh);
	}

	updateSoftClipLimits {
		xClipLow = xLimitLow + softClipMargin;
		xClipHigh = xLimitHigh - softClipMargin;
		yClipLow = yLimitLow + softClipMargin;
		yClipHigh = yLimitHigh - softClipMargin;
	}

	// The margin padded on x and y limits to catch in SC
	// before sending instruction to grbl.
	// .catchSoftLimit_ must be set to true to clip the instruction.
	// This prevents soft limit ALARM state in grbl.
	softClipMargin_ { |margin|
		softClipMargin = margin;
		this.updateSoftClipLimits
	}

	free {
		this.unfollow;
		followSynthXY !? { followSynthXY.free };
		followResponderXY !? { followResponderXY.free };

		super.free;
	}
}