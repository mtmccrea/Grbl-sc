a.free
SerialPort.devices
SerialPort.closeAll
s.boot

// a = ArduinoGRBL("/dev/tty.usbserial-A9CBV1D5", 115200)
a = ArduinoGRBL("/dev/tty.usbserial-A9ETD3VR", 115200)
a.unlock
a.home

s.boot
c = ControlFade(fadeTime:3)
c.lfo_(SinOsc, 15.reciprocal, -45, 45)

a.followSerialBuf_(c.controlBus.bus, 85)
a.unfollow
a.postState = true; a.state


// first time setting or when resetting EEPROM
a.send("$100=444.444")	// x, step/mm
a.send("$101=444.444")	// y, step/mm
a.maxTravelX_(292.000)	// set max X travel in mm -150 +145 -3 in case
a.maxTravelY_(164.000)	// set max y travel in mm -84 +83 - 3 in case
a.send("$25=750.000")	// (homing seek, mm/min)


// Note seize feed and speeds: accel 100 mm/min^2, feed 5500 mm/min
// 15% reduction in each
a.send("$120=85")		// max accel x mm/min
a.send("$121=85")		// max accel y mm/min
a.send("$110=4675")		// max rate x mm/min
a.send("$111=4675")		// max rate x mm/min


a.send("$22=1")			// make sure homing is on
a.send("$20=1")			// set soft limit on/off
a.send("$21=1")			// set hard limit on/off
a.send("$27=5")			// homing pulloff

a.settings				// confirm

a.streamBuf

a.postState = true; a.state
a.reset
a.unlock

//  HOME
a.home
a.worldOffset_(-150, -84); // a.send("G10L2P0X-150Y-84")


// travel to origin
a.goTo_(0,0,2000) // go to 0,0, feedrate 2000



// update the position class internally, optionally display it with postStateBool
a.updateState_(true, updateRate: 5, postStateBool: true)
a.updateState_(false)
a.postState = true
a.postState = false
a.stateUpdateRate

a.state

// BOOT

s.boot

// write the motors' position to a bus for following
a.writePosToBus_(true) // writes at a.stateUpdateRate rate
a.postState_(false)
a.posBus

// plot the motor only
~plot = ControlPlotter(a.posBus.bus, plotLength: 50, refresh_rate: 10).start.bounds_(-90,90);


// a control to drive the position (X)
c = ControlFade(fadeTime:3)

// plot both motor and control signal
~plot = ControlPlotter([c.busnum, a.posBus.bus], plotLength: 50, refresh_rate: 10).start.bounds_(-90,90);

c.lfo_(SinOsc, 35.reciprocal, 0, 45)
c.free

c.freq_(8.reciprocal)

c.lfoParams_(15.reciprocal, low: -15, high: 15) // span: 70 deg, in 30.reciprocal/2 seconds
c.lfoParams_(35.reciprocal, low: -35, high: 35) // span: 70 deg, in 30.reciprocal/2 seconds




c.lfo_(LFDNoise3, 6.reciprocal, -25, 25)
c.freq_(0.8)
c.low_(-23)
c.high_(54)

c.lfo_(LFTri, 6.reciprocal, -45, 45)
c.freq_(12.reciprocal)

c.value_(0)
c.toggleSource
c.fadeTime_(3)

c.free
c.high
c.low

///////////////
////  FOLLOW
///////////////

a.followSerialBuf_(c.controlBus, 40, gui: true) //135)
a.followSerialBuf_(c, 20, gui: false) //135)
a.dependants.postln; a.unfollow

a.motorInstructionRate = 240
a.motorInstructionRate = 40

a.stateUpdateRate = 40  // increase the rate that motor postition is updated
a.stateUpdateRate = 13  // increase the rate that motor postition is updated

a.maxDegPerSec_( 180 / 3); // 180 / 3.2702541628 default
a.maxDegPerSec_( 180 / 2.6); // 180 / 3.2702541628 default
a.maxDegPerSec_( 180 / 2.2); // 180 / 3.2702541628 default

a.send("$120=90")		// max accel x mm/min, benchmarked at 100
a.send("$121=90")		// max accel y mm/min, benchmarked at 100

a.send("$110=5300")		// max rate x mm/min
a.send("$111=5100")		// max rate y mm/min

a.send("$11=0.02")		// junction deviation, def 0.020
a.send("$11=0.02")		// junction deviation, def 0.020

a.minFeed_(3500)
a.minFeed_(4000)
a.minFeed_(5000)
a.motorInstructionRate_(140)

a.unfollow




// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
/// CONTROLLING X AND Y ///
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

// write the motors' position to a bus for following
a.writePosToBus_(true) // writes at a.stateUpdateRate rate
a.postState_(false)
a.posBus

// plot the motor only - XY
~plot = ControlPlotter(a.posBus.bus, 2, plotLength: 50, refresh_rate: 10).start.bounds_(-90,90);
a.goTo_(25, 35, 2000)


// a control to drive the position (X)
~ctlX = ControlFade(fadeTime:3)
~ctlY = ControlFade(fadeTime:3)

// plot both motor and control signal
// X
~plotX = ControlPlotter([~ctlX.busnum, a.posBus.bus], plotLength: 50, refresh_rate: 10).start.bounds_(-90,90);
// Y
~plotY = ControlPlotter([~ctlY.busnum, a.posBus.bus+1], plotLength: 50, refresh_rate: 10).start.bounds_(-90,90);


~ctlX.lfo_(SinOsc, 9.reciprocal, -45, 45)

~ctlY.lfo_(LFDNoise3, 2.3.reciprocal, 15, 76)


~ctlX.lfoParams_(15.reciprocal, low: -15, high: 15) // span: 70 deg, in 30.reciprocal/2 seconds
~ctlY.lfoParams_(25.reciprocal, low: -35, high: 35) // span: 70 deg, in 30.reciprocal/2 seconds


~ctlX.lfo_(LFDNoise3, 6.reciprocal, -25, 25)
~ctlX.lfo_(LFTri, 19.21.reciprocal, -25, 25)
~ctlX.lfo_(SinOsc, 19.21.reciprocal, -25, 25)
~ctlX.freq_(8.reciprocal)
~ctlX.freq_(0.8)
~ctlX.low_(-23)
~ctlX.high_(54)

~ctlY.lfo_(LFTri, 16.reciprocal, -15, 70)
~ctlY.lfo_(SinOsc, 8.2.reciprocal, -15, 70)
~ctlY.freq_(12.reciprocal)
~ctlY.low_(-10)
~ctlX.high_(65)


c.value_(0)
c.toggleSource
c.fadeTime_(3)

c.free
c.high
c.low

a.unfollow

///////////////
////  FOLLOW XY
///////////////

a.followSerialBufXY_(~ctlX.controlBus, ~ctlY.controlBus, 40, gui: true) //135)
a.debug_(false)
a.unfollow

a.followSerialBuf_(c, 20, gui: false) //135)
a.dependants.postln; a.unfollow

a.motorInstructionRate = 240
a.motorInstructionRate = 40
a.motorInstructionRate = 45
a.motorInstructionRate = 50
a.motorInstructionRate = 60
/*temp*/ a.followSynthXY.sendRate_(40)

a.stateUpdateRate = 40  // increase the rate that motor postition is updated
a.stateUpdateRate = 13  // increase the rate that motor postition is updated

a.maxDegPerSec_( 180 / 3); // 180 / 3.2702541628 default
a.maxDegPerSec_( 180 / 2.6); // 180 / 3.2702541628 default
a.maxDegPerSec_( 180 / 2.2); // 180 / 3.2702541628 default

a.send("$120=90")		// max accel x mm/min, benchmarked at 100
a.send("$121=90")		// max accel y mm/min, benchmarked at 100

a.send("$110=5300")		// max rate x mm/min
a.send("$111=5100")		// max rate y mm/min

a.send("$11=0.02")		// junction deviation, def 0.020

a.minFeed_(3500)
a.minFeed_(4000)
a.minFeed_(5000)
a.motorInstructionRate_(140)

a.unfollow








// // original method
// a.follow_(c, driveRate:18)
//
// a.followWithLeadingBuffer_(c, driveRate:10, instructionLag: 2)
// a.unfollow
// a.minFeed_(1500)
//
// a.motorInstructionRate_(30)

// a.maxDegPerSec = 180/22.5
a.maxDegPerSec = 55.041593417278 // x's max rate
a.maxFeed = 4675


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