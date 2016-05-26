// TODO:
//benchmark Y feed rates (more intervals + faster updates)
//Get better X feed rate resolution (more intervals + faster updates)

/*  ----- interval oscillator ------

freq, range, center
~ or ~
freq, hi, low

An oscillation frequency is chosen by which to generate random new destinations within a range.

Each new destination is divided into d equal intervals.

Initially 2 intervals are scheduled.

Once the motor has gone nextInstructionPercent of a interval, a third is scheduled, and then next
nextInstructionPercent is calculated. Once the motor has traveled past the nextInstructionPercent at d-1, another destination is chosen.

*/
a.free
SerialPort.devices
SerialPort.closeAll

a = ArduinoGRBL("/dev/tty.usbserial-A9CBV1D5", 115200)
a = ArduinoGRBL("/dev/tty.usbserial-A9KBFHXP", 115200)
a = ArduinoGRBL("/dev/tty.usbserial-A9M55Z3B", 115200)
a = ArduinoGRBL("/dev/tty.usbserial-A9ONJ9TP", 115200)
a = ArduinoGRBL("/dev/tty.usbserial-A9ETD3VR", 115200)

a.unlock
a.reset

a.home
a.postState = true; a.state

// travel to origin
a.goTo_(0,0,2000)
a.goTo_(0,5,2000)
a.goTo_(150,84,2000)
a.goTo_(50,45,2000)

// update the position class internally, optionally display it with postStateBool
a.updateState_(true, updateRate: 8, postStateBool: true)
a.updateState_(false)
a.postState = true
a.postState = false

a.postState = true; a.state

s.boot;
a.writePosToBus_(false)

a.plotMotorPositions_(true, plotRefreshRate: 8)

// testing the MotorDriver class
m = MotorDriver(a, xLow: -45, xHigh: -10, yLow: 5, yHigh: 30)
m.free
m.centerX_(20, 44)
m.centerY_(20, 56)
m.rangeY_(22)
m.rangeX_(322)
m.basePeriod_(14)
m.basePeriodDiv_(3)

m.driveRandom
m.drive

m.drivePeriodic(basePeriod: 20, baseAxis: 0, rateMultiple: 3)
m.drive

m.bounds_(-70, 70, 40, 65)
m.bounds_(-30, 30, 20, 35)
m.bounds_(10, 40, -10, 65)

m.getBounds

m.rateMult
m.rateMultiple_(4)
m.basePer
m.basePeriod_(30)

m.cyclePeriod_(2);
m.bounds_(-23, -3, -15, 5)
m.bounds_(-45, 45, -45, 45)

m.drivePeriodic_(basePeriod: 10, baseAxis: 0, rateMultiple: 2)

// note the lag of stopping the oscillation is
// as much as (cyclePeriod / numIntervals) * 2
m.stop

a.plotMotorPositions_(false)

m.cyclePeriod

m.free

/// ----------------------------------------------
/// below is the manual prototype of MotorDriver

// note the lag of stopping the oscillation is
// as much as ~randFreq.reciprocal / numIntervals * 2
a.stateAction_({})

~randFreq = 8.reciprocal;
~randFreq = 1;

// set feed rate envelope
(
var
initialized, started, origin, destination, numIntervals, feed, degPerSec,
intervalSizes, travelDistances, travelDistsAbs,
checkPointsOffsets, checkPoints, nextCheckPoints, checkPointDex, scheduleDex,
directions, nextDirections, maxTravelAbs, maxTravelAxisDex, maxTravelSigned,
intervalDestinations, nextIntervalDestinations, lastScheduled,
test, wPos, myClock, destTimeEstimate, trackedDex;

~randFreq = 3.reciprocal;
numIntervals = 4;

~feedsSpeeds = [
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

~feeds = ~feedsSpeeds.collect{|fs| fs[0]};
~speeds = ~feedsSpeeds.collect{|fs| fs[1]};
~feedRateEnv = Env(~feeds, ~speeds.differentiate.drop(1));

initialized = false;
started = false;

myClock = TempoClock();

~chooseDestination = { |lox, hix, loy, hiy|
	var trycount = 0;

	maxTravelAbs = 0; // init to 0 to get into the while loop

	block{ |break|
		while ( { maxTravelAbs < 1.5 }, // force it to travel at least this amount (deg)
			{
				"in while".postln;

				// origin of the move, which is the last destination,
				// unless the process hasn't yet been initialized
				origin = if( initialized.not,
					// in the case of timing out, initialized is false
					// so it first tries to start from the lastScheduled move
					{ lastScheduled ?? a.wPos[0..1] ?? [0,0]},
					{ destination }
				);

				// the randomly chosen destination
				destination = [
					rrand(lox, hix).round(0.1),
					rrand(loy, hiy).round(0.1)
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
					destination = [[lox, loy], [hix, hiy]].sum.collect(_.half).round(0.1); // split take the middle of hi/lo
					travelDistances		= destination - origin;
					travelDistsAbs		= travelDistances.abs;
					maxTravelAbs		= travelDistsAbs.maxItem;			// the distance to track, abs
					maxTravelAxisDex	= travelDistsAbs.maxIndex;			// 0 if x, 1 if y
					break.("BREAKING OUT".postln;)
				};

				trycount = trycount + 1;
		});
	};
};

// choose a new destination based on frequency and distance covered
~initNextMove = { |lox = -5, hix = 5, loy = 5, hiy = 10|

	~chooseDestination.(lox, hix, loy, hiy);

	// calc feed speed, just use the feed for the axis that travels farthest
	degPerSec = maxTravelAbs / ~randFreq.reciprocal;
	feed = ~feedRateEnv.at(degPerSec).round(1);

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
};

~init = {
	"INITIALIZING".postln;
	checkPointDex = 0;

	~initNextMove.();
	directions = travelDistances.sign;
	trackedDex = maxTravelAxisDex;
	postf("trackedDex %\ndirection\t %\n", trackedDex, directions);

	// pre-calculate checkpoints and intermediate steps
	// to the final destination
	checkPoints = numIntervals.collect{ |i|
		origin + (( (i*intervalSizes) + checkPointsOffsets ) * directions)
	};
	intervalDestinations = numIntervals.collect{ |i|
		(origin + ((i+1) * intervalSizes * directions)).round(0.001);
	};

	// debug
	"\tcheckpoints".postln;
	checkPoints.do(_.postln);
	"\tintervalDestinations".postln;
	intervalDestinations.do(_.postln); "".postln;

	// just beginning - schedule the first 2 intervals
	2.do{ |i|
		a.goTo_(intervalDestinations[i][0], intervalDestinations[i][1], feed);
		// debug
		postf("\tScheduling % at %\n", intervalDestinations[i], feed);
	};
	//	a.x_(intervalDestinations[0], feed);
	//	a.x_(intervalDestinations[1], feed);

	scheduleDex = 2;
	initialized = true;
};



// upon every motor position update:
a.stateAction_({

	// set initial state
	if( initialized.not, { ~init.() });

	test = if( directions[trackedDex].isPositive,
		{a.wPos[trackedDex] > checkPoints[checkPointDex][trackedDex]},
		{a.wPos[trackedDex] < checkPoints[checkPointDex][trackedDex]}
	);

	postf("test % % than %\t%\n",
		a.wPos[trackedDex], if(directions[trackedDex].isPositive, {"greater"},{"less"}), checkPoints[checkPointDex][trackedDex], test
	);

	if( test,
		// has it passed a checkpoint?
		{
			started = true;

			case
			{ checkPointDex < (numIntervals - 2) } {
				// schedule the next interval (2 ahead of current)
				a.goTo_(intervalDestinations[scheduleDex][0], intervalDestinations[scheduleDex][1], feed);
				// a.x_( intervalDestinations[scheduleDex], feed );
				lastScheduled = intervalDestinations[scheduleDex];
				postf("\tScheduling % at %\n", intervalDestinations[scheduleDex], feed);

				if( checkPointDex == 0, {
					myClock.clear; // clear the former timeout
					myClock.sched( destTimeEstimate, {
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

				~initNextMove.();

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
				a.goTo_(nextIntervalDestinations[scheduleDex][0], nextIntervalDestinations[scheduleDex][1], feed);
				lastScheduled = nextIntervalDestinations[scheduleDex];
				postf("\tScheduling % at %\n", nextIntervalDestinations[scheduleDex], feed);

				// postf("\tScheduling % at %\n", nextIntervalDestinations[scheduleDex], feed);
				// a.x_( nextIntervalDestinations[scheduleDex], feed );
				// lastScheduled = nextIntervalDestinations[scheduleDex];
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
				a.goTo_(intervalDestinations[scheduleDex][0], intervalDestinations[scheduleDex][1], feed);
				lastScheduled = intervalDestinations[scheduleDex];
				postf("\tScheduling % at %\n", intervalDestinations[scheduleDex], feed);

				// postf("\tScheduling % at %\n", intervalDestinations[scheduleDex], feed);
				// a.x_( intervalDestinations[scheduleDex], feed );
				// lastScheduled = intervalDestinations[scheduleDex];
			};

			checkPointDex	= (checkPointDex + 1) % numIntervals;
			scheduleDex	= (scheduleDex + 1) % numIntervals;
		}
	);
})
)
// if travelDistances < 3 degrees, schedule the intervals based on a time, not a checkpoint




)

// The rate envelope
 F	  -		time/180deg ...   deg/s
F50   - 215.80764153	... 0.83407611854643
F150  - 72.000130548	... 2.4999954670916
F250  - 43.232017320999 ... 4.16358086331
F500  - 21.728049221001 ... 8.2842227651998
F1000 - 10.992139706002 ... 16.375337724438
F1500 - 7.5356096239993 ... 23.886587679215
F2000 - 5.7919872950006 ... 31.077416235938
F2500 - 4.8480637210014 ... 37.128224866405
F3000 - 4.0957518770001	... 43.947974732258
F4000 - 3.4078305009999	... 52.819528420555
F4500 - 3.199816324		... 56.25322886502
F5000 - 3.0718647369999	... 58.596330050585
F5200 - 2.9519501004997	... 60.976640482347
F5400 - 2.9600448429992	... 60.809889561544
(
~feedsSpeeds = [
50, 0.0, // initial value
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

~feeds = ~feedsSpeeds.collect{|fs| fs[0]};
~speeds = ~feedsSpeeds.collect{|fs| fs[1]};

~feedRateEnv = Env(~feeds, ~speeds.differentiate.drop(1));
)

// ~feedRateEnv.plot

~feedRateEnv.at(3)
~feedRateEnv.at(47)
~feedRateEnv.at(59)