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
// a = ArduinoGRBL("/dev/tty.usbserial-A9KBFHXP", 115200)
a.unlock
a.home

// travel to origin
a.goTo_(0,60,2000) // go to 0,0

// update the position class internally, optionally display it with postStateBool
a.updateState_(true, updateRate: 8, postStateBool: true)
a.updateState_(false)
a.postState = true
a.postState = false

a.postState = true; a.state

s.boot;
a.writePosToBus_(true)

~plot = ControlPlotter(a.posBus.bus, 1, 50, 10, 'points').start.bounds_(0,90)

a.stateAction_({})

~randFreq = 2.reciprocal;
~randFreq = 1;

// set feed rate envelope
(
var
initialized, started, origin, destination, numIntervals, feed, degPerSec,
intervalSize, travelDistance, travelDistAbs,
checkPointsOffset, checkPoints, nextCheckPoints, checkPointDex, scheduleDex,
direction, nextDirection,
intervalDestinations, nextIntervalDestinations, lastScheduled,
test, wPos, myClock, destTimeEstimate;

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
~randFreq = 6.reciprocal; //2.reciprocal;

initialized = false;
started = false;
numIntervals = 3;

myClock = TempoClock();


// choose a new destination based on frequency and distance covered
~calcNewDest = { |lo = 15, hi = 90|
	var trycount = 0;

	origin = if( initialized.not,
		{ lastScheduled ?? a.wPos[0] ?? 0},
		{destination}
	);

	destination = rrand(lo, hi).round(0.1); // the randomly -chosen destination
	travelDistance = destination - origin;
	travelDistAbs = travelDistance.abs;

	// force it to travel at least this amount (deg)
	block{ |break|
		while ( { travelDistAbs < 1.5 }, {
			"in while".postln;
			destination = rrand(lo, hi).round(0.1);
			travelDistance = destination - origin;
			travelDistAbs = travelDistance.abs;
			postf("origin %\tdestination %\ttravelDistAbs %\ttravelDistance %\t", origin, destination, travelDistAbs, travelDistance);
			(trycount > 5).if{
				"forcing a destination after 5 tries".warn;
				destination = [lo, hi].sum.half.round(0.1); // split take the middle of hi/lo
				travelDistance = destination - origin;
				travelDistAbs = travelDistance.abs;
				break.("BREAKING OUT".postln;)
			};
			trycount = trycount + 1;
		});
	};

	degPerSec = travelDistAbs / ~randFreq.reciprocal;
	feed = ~feedRateEnv.at(degPerSec).round(1);
	intervalSize = travelDistAbs / numIntervals;
	checkPointsOffset = intervalSize * 0.25;

	// make estimate slightly longer to account for variance,
	// as it's used as a fail-safe if a destination isn't achieved.
	// note: timeout is scheduled after the first checkpoint is crossed (checkPointsOffset)
	destTimeEstimate = (travelDistAbs - checkPointsOffset) * 1.75 / degPerSec;

	postf("\nGO TO % at %\n", destination, feed);

	postf("
		\torigin %
		\ttravelDistance %
		\tdegPerSec %
		\tfeed %
		\tintervalSize %
		\tcheckPointsOffset %
		\tdestTimeEstimate %\n",
		origin,
		travelDistance,
		degPerSec,
		feed,
		intervalSize,
		checkPointsOffset,
		destTimeEstimate
	);
};

~init = {
	"INITIALIZING".postln;
	checkPointDex = 0;

	~calcNewDest.();
	direction = travelDistance.sign;
	postf("\t\tdirection\t %\n", direction);

	// pre-calculate checkpoints and intermediate steps
	// to the final destination
	checkPoints = numIntervals.collect{ |i|
		origin + (( (i*intervalSize) + checkPointsOffset ) * direction)
	};
	intervalDestinations = numIntervals.collect{ |i|
		(origin + ((i+1) * intervalSize * direction)).round(0.001);
	};

	"checkpoints".postln;	checkPoints.postln;
	"intervalDestinations".postln;	intervalDestinations.postln; "".postln;

	// just beginning - schedule the first 2 intervals
	a.x_(intervalDestinations[0], feed);
	a.x_(intervalDestinations[1], feed);
	postf("\tScheduling % at %\n", intervalDestinations[0], feed);
	postf("\tScheduling % at %\n", intervalDestinations[1], feed);

	scheduleDex = 2;
	initialized = true;
};



// upon every motor position update:
a.stateAction_({

	// set initial state
	if( initialized.not, { ~init.() });

	test = if( direction.isPositive,
		{a.wPos[0] > checkPoints[checkPointDex]},
		{a.wPos[0] < checkPoints[checkPointDex]}
	);

	postf("test % % than %\t%\n",
		a.wPos[0], if(direction.isPositive, {"greater"},{"less"}), checkPoints[checkPointDex], test
	);

	if( test,
		// has it passed a checkpoint?
		{
			started = true;

			case
			{ checkPointDex < (numIntervals - 2) } {
				// schedule the next interval (2 ahead of current)
				a.x_( intervalDestinations[scheduleDex], feed );
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

				~calcNewDest.();

				nextDirection = travelDistance.sign;
				postf("nextDirection\t %", nextDirection);

				// pre-calculate checkpoints and intermediate steps to the final destination
				nextCheckPoints = numIntervals.collect{|i|
					origin + (( (i*intervalSize) + checkPointsOffset ) * nextDirection)
				};
				nextIntervalDestinations = numIntervals.collect{|i|
					(origin + ((i+1) * intervalSize * nextDirection)).round(0.001);
				};

				"\nNEXT checkpoints".postln;	nextCheckPoints.postln;
				"NEXT intervalDestinations".postln;	nextIntervalDestinations.postln; "".postln;

				// schedule the next interval (2 ahead of current),
				postf("\tScheduling % at %\n", nextIntervalDestinations[scheduleDex], feed);
				a.x_( nextIntervalDestinations[scheduleDex], feed );
				lastScheduled = nextIntervalDestinations[scheduleDex];
			}

			// last interval checkpoint in the current destination
			{ checkPointDex > (numIntervals - 2) } {

				"\n\t- flipping to new direction, checkpoints, intervalDestinations -\n".postln;

				// update the checkpoint variables for the next checkpoint test
				direction = nextDirection;
				checkPoints = nextCheckPoints;
				intervalDestinations = nextIntervalDestinations;

				// schedule the next interval(2 ahead of current),
				// on the way to the new destination
				postf("\tScheduling % at %\n", intervalDestinations[scheduleDex], feed);
				a.x_( intervalDestinations[scheduleDex], feed );
				lastScheduled = intervalDestinations[scheduleDex];
			};

			checkPointDex	= (checkPointDex + 1) % numIntervals;
			scheduleDex	= (scheduleDex + 1) % numIntervals;
		}
	);
})
)
// if travelDistance < 3 degrees, schedule the intervals based on a time, not a checkpoint




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