MotorDriver {
	// copyArgs
	var <motor, <randFreq, <xLo, <xHi, <yLo, <yHi, <numIntervals, <stateRefreshRate;

	var
	<driveRoutine, feedsSpeeds, feeds, speeds, <feedRateEnv,
	<initialized, <started, <origin, <destination, <feed, <degPerSec,
	intervalSizes, travelDistances, travelDistsAbs,
	checkPointsOffsets, checkPoints, nextCheckPoints, checkPointDex, scheduleDex,
	directions, nextDirections, maxTravelAbs, maxTravelAxisDex, maxTravelSigned,
	intervalDestinations, nextIntervalDestinations, lastScheduled,
	test, <timeoutClock, destTimeEstimate, trackedDex;

	*new { |anArduinoGRBL, randFreq = 0.2, xLow = -45, xHigh = 45, yLow = 0, yHigh = 45, numIntervals = 4, stateRefreshRate = 8|
		^super.newCopyArgs(anArduinoGRBL, randFreq, xLow, xHigh, yLow, yHigh, numIntervals, stateRefreshRate ).init;
	}

	stateRefreshRate_ { |newRate| newRate !? { motor.stateUpdateRate_(newRate) } }

	numIntervals_ { |num| (num >= 3).if{ numIntervals = num} }

	randFreq_ { |freq| (freq < 1).if( { randFreq = freq }, { "rate is currently limited to less than 1 Hz.".warn; randFreq = 1 } ) }

	bounds_ { |xLow, xHigh, yLow, yHigh|
		xLow	!? { xLo = xLow.clip(motor.xBoundLow, motor.xBoundHigh) };
		xHigh	!? { xHi = xHigh.clip(motor.xBoundLow, motor.xBoundHigh) };
		yLow	!? { yLo = yLow.clip(motor.yBoundLow, motor.yBoundHigh) };
		yHigh	!? { yHi = yHigh.clip(motor.yBoundLow, motor.yBoundHigh) };
	}

	init {

		(numIntervals < 3).if{
			numIntervals = 4;
			"Must use 3 or more intervals for smooth movement, setting to 4".warn
		};
		(stateRefreshRate < 5).if{
			stateRefreshRate = 5;
			"Must update motor state at 5 Hz for accurate tracking, setting to 5".warn;
		};

		// make sure the motor position state is being updated
		motor.updateState_(true, stateRefreshRate, false);

		// TODO: move this elsewhere
		feedsSpeeds = [
			// feerate, deg/sec
			50, 0.0, 				// initial value
			50, 0.83407611854643,
			150, 2.4999954670916,
			250, 4.16358086331,
			500, 8.2842227651998,
			1000, 16.375337724438,
			1500, 23.886587679215,
			2000, 31.077416235938,
			2500, 37.128224866405,
			3000, 43.947974732258,
			4000, 52.819528420555,
			4500, 56.25322886502,
			5000, 58.596330050585,
			5200, 60.976640482347,
			// 5400, 60.809889561544,
		].clump(2);

		feeds = feedsSpeeds.collect{|fs| fs[0]};
		speeds = feedsSpeeds.collect{|fs| fs[1]};
		feedRateEnv = Env( feeds, speeds.differentiate.drop(1) );

		initialized = false;
		started = false;
		timeoutClock = TempoClock();

		// this.drive; // user calls .drive?
	}

	initTravel {
		"INITIALIZING".postln;
		checkPointDex = 0;

		this.initNextMove;

		directions = travelDistances.sign;
		trackedDex = maxTravelAxisDex;
		postf("trackedDex %\ndirection\t %\n", trackedDex, directions);

		// pre-calculate checkpoints and intermediate steps to the final destination
		checkPoints = numIntervals.collect{ |i|
			origin + (( (i*intervalSizes) + checkPointsOffsets ) * directions)
		};
		intervalDestinations = numIntervals.collect{ |i|
			(origin + ((i+1) * intervalSizes * directions)).round(0.001);
		};

		// debug
		"\tcheckpoints".postln; checkPoints.do(_.postln);
		"\tintervalDestinations".postln; intervalDestinations.do(_.postln);

		// just beginning - schedule the first 2 intervals
		2.do{ |i|
			motor.goTo_(intervalDestinations[i][0], intervalDestinations[i][1], feed);
			// debug
			postf("\tScheduling % at %\n", intervalDestinations[i], feed);
		};

		scheduleDex = 2;
		initialized = true;
	}

	// choose a new destination based on frequency and distance covered
	initNextMove {

		this.chooseDestination;

		// calc feed speed, just use the feed for the axis that travels farthest
		degPerSec = maxTravelAbs / randFreq.reciprocal;
		feed = feedRateEnv.at(degPerSec).round(1);

		intervalSizes = travelDistsAbs / numIntervals;
		checkPointsOffsets = intervalSizes * 0.25;

		// make estimate slightly longer to account for variance,
		// as it's used as a fail-safe if a destination isn't achieved.
		// note: timeout is scheduled after the first checkpoint is crossed (checkPointsOffset)
		// note: this only applies to the distance that's tracked (maxTravel)
		destTimeEstimate = (maxTravelAbs - checkPointsOffsets[maxTravelAxisDex]) * 1.5 / degPerSec;

		postf("\nGO TO % at %\n", destination, feed);

		postf("
			\torigin %
			\ttravelDistances %
			\tdegPerSec %
			\tfeed %
			\tintervalSize %
			\tcheckPointsOffsets %
			\tdestTimeEstimate %\n",
			origin,
			travelDistances,
			degPerSec,
			feed,
			intervalSizes,
			checkPointsOffsets,
			destTimeEstimate
		);
	}


	chooseDestination {
		var trycount = 0;

		// init to 0 to get into the while loop
		maxTravelAbs = 0;

		block{ |break|
			while ( { maxTravelAbs < 1.5 }, // force it to travel at least this amount (deg)
				{
					"in while".postln;

					// origin of the move, which is the last destination,
					// unless the process hasn't yet been initialized
					origin = if( initialized.not,
						// in the case of timing out, initialized is false
						// so it first tries to start from the lastScheduled move
						{ lastScheduled ?? motor.wPos[0..1] ?? [0,0]},
						{ destination }
					);

					// the randomly chosen destination
					destination = [
						rrand(xLo, xHi).round(0.1),
						rrand(yLo, yHi).round(0.1)
					];

					// find which travel distance is greater, x or y
					travelDistances		= destination - origin;
					travelDistsAbs		= travelDistances.abs;
					maxTravelAbs		= travelDistsAbs.maxItem;			// the distance to track, abs
					maxTravelAxisDex	= travelDistsAbs.maxIndex;			// 0 if x, 1 if y

					postf("origin %\ndestination %\ntravelDistsAbs %\ntravelDistances %\nmaxTravelAbs %\nmaxTravelAxisDex %\n",
						origin, destination, travelDistsAbs, travelDistances, maxTravelAbs, maxTravelAxisDex);

					(trycount > 5).if{
						"forcing a destination after 5 tries".warn;
						destination = [[xLo, yLo], [xHi, yHi]].sum.collect(_.half).round(0.1); // split take the middle of hi/lo
						travelDistances		= destination - origin;
						travelDistsAbs		= travelDistances.abs;
						maxTravelAbs		= travelDistsAbs.maxItem;			// the distance to track, abs
						maxTravelAxisDex	= travelDistsAbs.maxIndex;			// 0 if x, 1 if y
						break.("BREAKING OUT".postln;)
					};

					trycount = trycount + 1;
			});
		};
	}

	// upon every motor position update:
	drive {
		driveRoutine.notNil.if(
			{
				driveRoutine.isPlaying.not.if{ driveRoutine.reset.play }
			},{
				driveRoutine = Routine.run({

					inf.do{

					// set initial state
					if( initialized.not, { this.initTravel });

					test = if( directions[trackedDex].isPositive,
						{motor.wPos[trackedDex] > checkPoints[checkPointDex][trackedDex]},
						{motor.wPos[trackedDex] < checkPoints[checkPointDex][trackedDex]}
					);

					postf("test % % than %\t%\n",
						motor.wPos[trackedDex], if(directions[trackedDex].isPositive, {"greater"},{"less"}),
						checkPoints[checkPointDex][trackedDex], test
					);

					if( test,
						// has it passed a checkpoint?
						{
							started = true;

							case
							{ checkPointDex < (numIntervals - 2) } {
								// schedule the next interval (2 ahead of current)
								motor.goTo_(intervalDestinations[scheduleDex][0], intervalDestinations[scheduleDex][1], feed);
								lastScheduled = intervalDestinations[scheduleDex];
								postf("\tScheduling % at %\n", intervalDestinations[scheduleDex], feed);

								if( checkPointDex == 0, {
									timeoutClock.clear; // clear the former timeout
									timeoutClock.sched( destTimeEstimate, {
										initialized = false;	// so re-initializes on next state update
										started = false;		// so sets origin as current work position
										"\nTIMED OUT - reinitializing".warn
										}
									);
								})
							}

							// second-to-last interval indexes before the destination
							{ checkPointDex == (numIntervals - 2) } {

								"\n\t- planning a new destination -\n".postln;

								this.initNextMove;

								nextDirections = travelDistances.sign;
								postf("nextDirections\t %", nextDirections);

								// pre-calculate checkpoints and intermediate steps to the final destination
								nextCheckPoints = numIntervals.collect{|i|
									origin + (( (i*intervalSizes) + checkPointsOffsets ) * nextDirections)
								};
								nextIntervalDestinations = numIntervals.collect{|i|
									(origin + ((i+1) * intervalSizes * nextDirections)).round(0.001);
								};

								"\nNEXT checkpoints".postln;	nextCheckPoints.postln;
								"NEXT intervalDestinations".postln;	nextIntervalDestinations.postln; "".postln;

								// schedule the next interval (2 ahead of current)
								motor.goTo_(nextIntervalDestinations[scheduleDex][0], nextIntervalDestinations[scheduleDex][1], feed);
								lastScheduled = nextIntervalDestinations[scheduleDex];
								postf("\tScheduling % at %\n", nextIntervalDestinations[scheduleDex], feed);

							}

							// last interval checkpoint in the current destination
							{ checkPointDex > (numIntervals - 2) } {

								"\n\t- flipping to new direction, checkpoints, intervalDestinations -\n".postln;

								// update the checkpoint variables for the next checkpoint test
								directions = nextDirections;
								checkPoints = nextCheckPoints;
								intervalDestinations = nextIntervalDestinations;
								trackedDex = maxTravelAxisDex;

								// schedule the next interval(2 ahead of current),
								// on the way to the new destination
								motor.goTo_(intervalDestinations[scheduleDex][0], intervalDestinations[scheduleDex][1], feed);
								lastScheduled = intervalDestinations[scheduleDex];
								postf("\tScheduling % at %\n", intervalDestinations[scheduleDex], feed);

							};

							checkPointDex	= (checkPointDex + 1) % numIntervals;
							scheduleDex	= (scheduleDex + 1) % numIntervals;
						}
					);
					motor.stateUpdateRate.reciprocal.wait;
					}
				});

			}
		);

	}

	stop {
		driveRoutine.stop;
		initialized = false;	// so re-initializes on next state update
		started = false;		// so sets origin as current work position
	}
}