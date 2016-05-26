////////////////////////////////////////////
// gathing the max speeds/acc
////////////////////////////////////////////

a.free
SerialPort.devices
SerialPort.closeAll
s.boot

a = ArduinoGRBL("/dev/tty.usbserial-A9KBFHXP", 115200)
// a = ArduinoGRBL("/dev/tty.usbserial-A9CBV1D5", 115200)
a.postState = true; a.state;
a.mode
a.home
a.pause
a.reset
a.unlock

// first time setting or when resetting EEPROM
a.send("$100=444.444")	// x, step/mm
a.send("$101=444.444")	// y, step/mm
a.maxTravelX_(292.000)	// set max X travel in mm -150 +145 -3 in case
a.maxTravelY_(164.000)	// set max y travel in mm -84 +83 - 3 in case
a.send("$25=1250.000")	// (homing seek, mm/min)


// Note seize feed and speeds: accel 100 mm/min^2, feed 5500 mm/min
// 15% reduction in each
a.send("$120=75")		// max accel x mm/min, benchmarked at 100
a.send("$121=75")		// max accel y mm/min, benchmarked at 100
a.send("$110=4675")		// max rate x mm/min
a.send("$111=4675")		// max rate y mm/min


a.send("$22=1")			// make sure homing is on
a.send("$20=1")			// set soft limit on/off
a.send("$21=1")			// set hard limit on/off
a.send("$27=45")			// homing pulloff

// a.send("$11=0.008")		// junction deviation, def 0.020
a.send("$11=0.02")		// junction deviation, def 0.020

a.settings				// confirm
a.streamBuf

a.postState = true; a.state
a.reset
a.unlock

//  HOME
a.home
a.worldOffset_(-150, -84); // a.send("G10L2P0X-150Y-84")


// travel to world origin
a.goTo_(0,0,1000) // go to 0,0
a.goTo_(50,-50,1000) // go to 0,0


// test for faulty rig
a.send("$23=0")


// update the position class internally, optionally display it with postStateBool
a.updateState_(true, updateRate: 25, postStateBool: true)
a.updateState_(false)
a.postState = true
a.postState = false
a.stateUpdateRate

a.state



t =TempoClock()
t.elapsedBeats
t.stop

(
a.stateAction_({ |state, mpos, wpos|

});
)

~state = "Idle";
// define an action when receiving the motors' state
(
a.stateAction_({ |state, mpos, wpos|
	case
	{ (state == "Run") and: (~state == "Idle") }
	{ t =TempoClock(); "starting".postln; ~state = "Run" }
	{ (state == "Idle") and: (~state == "Run") }
	{ ~timer = t.elapsedBeats; t.stop; "Stopped".postln; ~state = "Idle"}
});
)

a.y_(70,3500)

~timer
a.pause
a.x_(-90, 4675)
/* X axis */

// 180 deg travel, accel max 40, maxFeed 6000
// F1000 - 11.19991911, 11.247940609
// F1500 - 7.855848721, 7.855626937
// F2000 - 6.239855228, 6.255757629
// F2500 - 5.407839813
// F3000 - 4.911977154
// F3500 - 4.543852548
// F4000 - 4.415837631
// F4500 - 4.3041549750001

// 180 deg travel, accel max 70, maxFeed 6000
// F3000 - 4.352082331
// F3500 - 3.951963209
// F4000 - 3.712087549
// F4500 - 3.5037895300001

// 180 deg travel, accel max 100, maxFeed 6000
/*
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
F5200 - 2.9519501004997	... 60.976640482347 deg/s
F5400 - 2.9600448429992	... 60.809889561544 deg/s
F5500 - seize
*/

~timer
a.x_(0, 4675)
// now at safe max speed: accel 85, feed 4675
F 4675 -
[3.2479491630002, 3.2637988589995, 3.295934363, 3.2956674460002, 3.2479209829999].mean
// >> 3.2702541628 per 180 deg
// >> 55.041593417278 deg/s


~timer
a.y_(-70,5500)
/* Y axis */

// 140 deg travel, accel max 70, maxFeed 6000
// F2000 - 4.7034966790002
// F3000 - 3.5357293480001
// F4500 - 3.103968416
// F5000 - 2.8797177899999, 2.9116916730002, 2.8799034399999
// F5300 - 2.863684315, 2.8958410319999
// F5500 - seize

// 140 deg travel, accel max 100, maxFeed 6000
// F2000 - 4.5596348130002
// F3000 - 3.29592624
// F4000 - 2.7997526200002, 2.7837459810003
// F4500 - 2.671917741, 2.6401306740004
// F5000 - 2.5599043570001, 2.543853833
// F5300 - 2.5117971129998, 2.4799864289998
// F5500 - seize

~timer
a.y_(-70, 4675)
// now at safe max speed: accel 85, feed 4675
F 4675 -
[2.7677044499997, 2.7358340520004, 2.7520096259996, 2.7518296389999, 2.75178443].mean
// >> 2.7518324393999 per 140 deg
// >> 50.875190653153 deg/s

// write the position to a bus to read into plotter
s.boot;

a.writePosToBus_(true)
a.postState_(false)
a.posBus

// go to new home, initialize feed rate
a.goTo_(0,0,4000)
a.x_(20)
a.y_(50)

a.state

// a control to drive the position (X)
c = ControlFade(fadeTime:3)

~plot = ControlPlotter([c.busnum, a.posBus.bus], plotLength: 50, refresh_rate: 10).start.bounds_(-90,90);

c.lfo_(SinOsc, 25.reciprocal, -45, 45)
c.lfoParams_(15.reciprocal, low: -15, high: 15) // span: 70 deg, in 30.reciprocal/2 seconds
c.lfoParams_(low: -10, high: 10)
c.freq_(10.reciprocal)


c.lfo_(LFNoise2, 3.reciprocal, -15, 15)
c.freq_(1.3)
c.low_(-35)
c.freq_(2.3)


c.value_(0)
c.toggleSource
c.fadeTime_(3)

c.free; ~plot.free

a.follow_(c, 18)

a.follow_(c, driveRate: 18, instructionLag:2)
a.unfollow
a.reset

a.motorInstructionRate_(18)

// a.maxDegPerSec = 180/22.5
a.maxDegPerSec = 180/6
a.maxFeed_(4000)

a.free


a.streamBuf.size
a.streamBuf.clear
(a.streamBuf.size < a.minMoveQueue)
a.starving
a.lagging

a.minMoveQueue = 2


(
~driveBus = Routine( {
	inf.do{
		c.controlBus.get({ |busnum, val|
			/*[busnum, val].postln*/
			// ~az = val.postln;
			a.x_(val, 1000);
		});
		1.wait // rate to send G-code positions to GRBL
	}
});
)
~driveBus.reset.play
~driveBus.stop

fork{ inf.do{ a.state; 0.333.wait }}




( // define a routine to request the state
r = Routine({
	var updateRate = 5, cnt = 0;
	inf.do{
		a.state;
		// cnt = cnt+1; cnt.postln;
		updateRate.reciprocal.wait;
	}
});
)