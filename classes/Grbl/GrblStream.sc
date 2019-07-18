// TODO: add path trimming

GrblStream {
	var <driver; // copyArgs
	var <>path;
	var <feedOverride=false; // true ignore's path's feeds
	var <defaultFeed, <playTask;
	var <streaming=false;
	var <startIdx=0, endIdx= -1;

	*new { |aGrblDriver|
		^super.newCopyArgs(aGrblDriver).init;
	}

	*newFromArray { |aGrblDriver, array|
		^super.newCopyArgs(aGrblDriver).initFromArray(array).init;
	}

	*newFromFile { |aGrblDriver, path, delim|
		^super.newCopyArgs(aGrblDriver).initFromFile(path, delim).init;
	}

	init {
		playTask = Task({
			var pathCnt;// = 0; // index of the path pointer
			var nextTarget;
			// give it enough headroom from 17
			// ~advanceWaittime.reciprocal - stateUpdateRate
			var plannerMax=6;
			var firstFeed;
			var sent, end;

			pathCnt = startIdx;

			/* Initialize feed and execute the first move */

			if (feedOverride and: defaultFeed.isNil) {
				Error("feedOverride is set, but no defaultFeed defined.").throw
			};

			if (defaultFeed.isNil and: path[pathCnt][2].isNil) {
				Error("defaultFeed undefined and first instruction does not contain a feed rate").throw
			} {
				firstFeed = path[pathCnt][2] ?? defaultFeed;
			};

			// go to first position, initialize feed
			nextTarget = path[pathCnt];
			sent = driver.submitMoveXY(
				nextTarget[0].round(0.001),
				nextTarget[1].round(0.001),
				if (feedOverride) {
					defaultFeed
				} {
					nextTarget[2] !? {nextTarget[2].round(1)} //~feed, nil is ok
				}
			);
			sent.not.if{ Error(
				"Couldn't send first instruction, RX buffer is full, wait and restart Task to execute path."
			).throw };
			pathCnt = pathCnt + 1;

			end = this.endIdx;

			/* Execute the rest of the path score */
			while ({
				pathCnt <= end
			}, {
				if (driver.mode != "Alarm") {
					var sent = false;

					nextTarget = path[pathCnt];

					// if there's room in the planning buffer...
					if (driver.pBuf<plannerMax) {
						// this will only send if the serial rx buffer has enough room
						// sent = this.submitMoveXY(
						sent = driver.submitMoveXY(
							nextTarget[0].round(0.001),
							nextTarget[1].round(0.001),
							if (feedOverride) {
								defaultFeed
							} {
								nextTarget[2] !? {nextTarget[2].round(1)} //~feed, nil is ok
							}
						);
						// this.changed(\sent, sent.asInteger);

						if (sent) {

							// postf("sent %: % \t% \t%\n",
							// 	pathCnt,
							// 	nextTarget[0].round(0.001),
							// 	nextTarget[1].round(0.001),
							// 	nextTarget[2] !? {nextTarget[2].round(1)} // ~feed, nil is ok
							// );
							pathCnt = pathCnt+1;
						};
					};

					// NOTE: the state of Grbl on the SC side is only updated at the
					// stateUpdateRate, so keep in mind that multiple cycles of
					// instructions may be sent before the planning buffer state
					// is updated. I.e. if the wait time is 3x faster than the stateUpdateRate
					// it may overshoot the planning buffer limit by that many instructions.
					// the max planning buffer is 17, so pad the plannerMax accordingly
					// ~advanceWaittime.wait;
					(driver.stateUpdateRate.reciprocal * 0.8).wait;
				} {
					"ALARM mode: won't advance".postln;
					1.wait;
				};
			}
			);

			this.changed(\status, "All path moves have been sent".postln );
			streaming = false;
			this.changed(\streaming, streaming);
		});
	}

	initFromArray { |arr|
		if (arr.rank != 2) {
			Error("Path array mush be a 2D array").throw;
		};
		path = [];

        postf("first instruction: %, feed %\n", arr[0], arr[0][2]);
		arr[0][2] ?? {
			"WARNING: ".post;
			"First move has an undefined feed rate, be sure to set a defaultFeed or instructions will be skipped until a feed is defined.".postln
		};

		arr.do{
			|xyf, i|
			var feed;

			if (xyf.size > 3) {
				format("Move location and feed at index % lists more than 3 items. Expecting only [x, y, feed]\n", i).warn;
			};

			xyf[2] !? {
				feed = xyf.at(2).asFloat;
                postf("feed provided %\n", feed);
				if (feed <= 0) {
					format("Found feed rate <= 0: %, at index %\n", feed, i).warn;
					feed = nil
				};
			};

			// filter out undefined feed rate or feed rate of 0
			if (feed.notNil){
				path = path.add(xyf.asFloat);
			} { // add position only
				path = path.add(xyf[0..1].asFloat);
			}
		};
        "initialized:".postln;
        path.postln;
         "finalized".postln;
		this.changed(\status, "Path updated.");
		this.changed(\pathLoaded);
	}

	initFromFile { |path, delim = $ |
		var arr;
		arr = FileReader(path, skipEmptyLines: true, skipBlanks: true, delimiter: delim);
		this.initFromArray(arr);
	}

	// .neg on move coords. useful if path is in positive
	// coords, but machine coords are negative
	invertCoords { |invXBool=true, invYBool=true|
		path ?? {
			Error("Path hasn't been defined. Set path variable, initFromArray, or initFromFile").throw
		};

		path.do{ |xyf, i|
			invXBool.if{
				path[i][0] = path[i][0].neg;
			};
			invXBool.if{
				path[i][1] !? {path[i][1] = path[i][1].neg};
			};
		}
	}

	startIdx_{ |idx|
		startIdx = idx.clip(0, path.size-1);
		if (startIdx>this.endIdx) {"startIdx is greater than endIdx".warn};
		this.changed(\startIdx, startIdx);
	}

	endIdx_{ |idx|
		endIdx = idx.clip(-1, path.size-1);
		if (startIdx>this.endIdx) {"endIdx is less than startIdx".warn};
		this.changed(\endIdx, endIdx);
	}

	endIdx {^if (endIdx == -1) {path.size-1} {endIdx}}

	length {^this.endIdx+1-startIdx}

	// approx time to execute path
	duration {
		var duration=0, lastPnt, idx=1;

		path ?? {Error("No movement path has been set").throw};

		if (path[startIdx][2].isNil and: defaultFeed.isNil) {
			Error("First move has no feedrate, and defaultFeed is undefined, cannot calculate path duration.").throw;
		};

		lastPnt = Point(
			path[startIdx][0],
			path[startIdx][1] ?? {Error("No Y coordinate defined in the first move").throw};
		);

		(this.length-1).do{
			// path.drop(1).do{
			// |xyf|
			var feed, thisPnt, dist, dur, xyf;
			xyf = path[idx];

			feed = if (feedOverride) {defaultFeed} {xyf[2] ?? defaultFeed};
			thisPnt = Point(xyf[0], xyf[1] ?? lastPnt.y);

			dist = lastPnt.dist(thisPnt) * 2.sqrt; // TODO: 2.sqrt for HBOT configuration
			dur = dist/feed*60;

			duration = duration + dur;
			lastPnt = thisPnt;
			idx = idx+1;
		};
		"Note: this duration applies to coreXY configuration".warn;
		^duration
	}

	goToStartPos { |feed|
		path ?? {Error("No movement path has been set").throw};

		driver.goTo_(
			path[startIdx][0],
			path[startIdx][1] ?? {Error("No Y coordinate defined in the first move").throw},
			feed ?? defaultFeed
		)
	}

	start {
		playTask !? {
			playTask.start;
			streaming = true;
			this.changed(\streaming, streaming);
		}
	}
	stop {
		playTask !? {
			playTask.stop;
			streaming = false;
			this.changed(\streaming, streaming);
		}
	}
	reset { |goToStart=true|
		playTask !? playTask.reset;
		goToStart.if {this.goToStartPos};
	}

	defaultFeed_ {|feed|
		defaultFeed = feed;
		this.changed(\defaultFeed, defaultFeed);
	}

	feedOverride_ {|bool|
		feedOverride = bool;
		this.changed(\feedOverride, bool);
	}

	getPnt { |idx|
		path ?? {Error("No movement path has been set").throw};
		^Point(path[idx][0], path[idx][1])
	}
}