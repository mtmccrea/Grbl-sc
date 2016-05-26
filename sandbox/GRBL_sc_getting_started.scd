/*
TODO
establish homing routine
- home, travel to 0 degrees x (left side i.e. -78 absolute)

set methods for:
- trtravel
- setting params
- getting params?

*/

SerialPort.devices
SerialPort.closeAll

a.free

a = ArduinoGRBL("/dev/tty.usbserial-A9KBFHXP", 115200)
a.unlock
a.home
a.state
a.port.putAll("$H")
a.state
a.pause
a.reset

a.send("G10L2P0X-150Y-84") // after homing, set world coords
// or...
a.worldOffset_(-150, -84);

( // define a routine to request the state - which updates the GUI
r = Routine({
	var updateRate = 5, cnt = 0;
	inf.do{
		a.state;
		// cnt = cnt+1; cnt.postln;
		updateRate.reciprocal.wait;
	}
});
)

r.reset.play
r.stop


( // define an action when receiving the motors' state
a.stateAction_({ |state, wpos, mpos|

	// // start/stop the state requester
	// switch(state,
	// 	"Run",	{ r.isPlaying.not.if{r.reset.play} },
	// 	"Idle",	{ "Stopped".postln; r.stop }
	// );

	// to update the GUI
	~az = wpos[0];
	// ~az.postln;

	// // post the position
	// format(
	// 	"% mode\tmachine position:\t%\n\t\t\tworld position:\t\t%\n",
	// 	state, mpos, wpos
	// ).postln;
});
)

a.pause
a.reset

a.streamBuf.clear

// go to new home, initialize feed rate
a.goTo_(0,0,1000)

a.maxTravelX_(292.000) // set max X travel in mm -150 +145 -3 just in case
a.maxTravelY_(164.000) // set max y travel in mm -84 +83 - 3 just in case
a.send("$22=1") // make sure homing is on
a.send("$20=1") // set soft limit on/off

a.x_(-140, 1000)

[90, 12, 20, -30].do{|destination| a.x_(destination) }
[[-40, 500], [12, 1000]].do{|destRate| a.x_(destRate[0], destRate[1]) }

a.unlock
a.state
a.settings

// a control to drive the position (X)

c = ControlFade(fadeTime:3)
c.plot
c.lfo_(SinOsc, 10.reciprocal, -90, 90)
c.freq_(50.reciprocal)
c.plotter.bounds_(-90,90)
c.value_(0)
c.toggleSource
c.fadeTime_(3)

c.lfoParams_(30.reciprocal, low: -35, high: 35) // span: 70 deg, in 30.reciprocal/2 seconds

c.lfoParams_(35.reciprocal, low: -35, high: 35) // span: 70 deg, in 30.reciprocal/2 seconds


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

( // define an action when receiving the motors' state
a.stateAction_({ |state, mpos, wpos|

	// start/stop the state requester
	switch(state,
		"Run",	{ r.isPlaying.not.if{r.reset.play} },
		"Idle",	{ "Stopped".postln; r.stop }
	);

	// post the position
	format(
		"% mode\tmachine position:\t%\n\t\t\tworld position:\t\t%\n",
		state, mpos, wpos
	).postln;
});
)

( // send a message and watch it go~
fork {
	// go to x:20 y:30
	a.send("G01X25Y90F400");
	0.2.wait;
	a.state; // send first request
}
)

a.state;
a.settings;
a.gCodeParams;

a.send("$#")
a.send("$G")

// proper setting of world coordinates
a.send("G10L20P0X0Y0") // after seeking to desired home, set world coords
a.send("G10L2P0X-150Y-84") // after homing, set world coords
// or...
a.worldHome_(-150, -84);

a.streamBuf

fork{ inf.do{ a.state; 0.333.wait }}
a.resume

a.port.putAll("!")
a.port.putAll("~\n")

a.x_(10, 500);
a.y_(0);
a.goTo_(0,0,1000)
a.reset
a.unlock


a.goTo_(90,-90)
a.goTo_(-90,30)
a.goTo_(90,-30)

x = fork{ inf.do{ a.goTo_(rrand(-100, 100),rrand(-100, 100),500); 0.2.wait } };
x.stop
a.pause
a.resume

a.send("G01X00Y00F400");
a.send("G01X00Y0F400");


a.state;
a.settings
a.reset
a.close

// original config
a.send("$3=6") 	// default // invert y, don't invert x
a.send("$23=0") // default // don't invert either
// homing routine
a.home
// go to absolute home
a.send("G01X-150Y-84F1000")
// set work home - BAD - isn't retained
a.send("G92X0Y0")

a.state

a.goTo_(140, -20)
a.go(-140, -65)
a.y_(45)
a.go(0,0)

~path = Array.series(5, 5, 5) ++ Array.series(5, 25, -5) ++ Array.series(6, 0, -5) ++ Array.series(6, -25, 5)

fork{ ~path.do{|i| a.x_(i); 0.1.wait} }

a.x_(0)
a.state
a.x_(14, 1000)
a.y_(14)
// now navigate relative to home
a.send("G01X-90Y75F400")

a.send("G01X-90Y0F400")

// arching x width: ~+/-145
// arching y width: ~+/-84

a.send("$130=292.000") // set max X travel in mm -150 +145 -3 just in case
a.send("$131=164.000") // set max y travel in mm -84 +83 - 3 just in case
a.send("$20=1") // set soft limit on/off


// new config to get pos x pos y instructions
a.send("$3=1") // invert x, don't invert y
a.send("$23=3") // invert both
// go to absolute home
a.send("G01X-50Y-115F1000")

a.send("G01X-140Y-115F1000")


a.send("G01X-185F400")
a.send("G01Y-185F400")

a.state
a.reset




a.home
a.state

a.send("G01X-150F400")
a.send("G01Y-185F400")
a.send("G01X-150Y-110F400")
a.state
a.pause

a.send("$130=270.000") // set max X travel in mm
a.send("$131=180.000") // set max y travel in mm

a.unlock
a.state
a.reset

a.send("G01X-15Y00F1000")
a.close
a.home

a.pause
a.resume

a.send("$H")
a.send("$N") // view startup blocks

// set grbl parameters

// (steps_per_revolution*microsteps)/mm_per_rev
// 50:1 gearing (50x down per step), * 16 microsteps (configured in motor driver wiring), treating mm as degrees (360mm/revolution)
// 200*50*16/360 = 444.444
// or put another way...
// For steps/degree(mm) 50*16 / 1.8 = 444.4444... steps per degree
a.send("$100=444.444") // x, step/mm
a.send("$101=444.444") // y, step/mm

a.send("$101")

a.send("$22=0") // enable homing
a.send("$130=270.000") // set max X travel in mm
a.send("$131=180.000") // set max y travel in mm
a.send("$20=0") // set soft limit on/off
a.settings

( // go again
fork {
	// go to x:20 y:30 again, note difference in machine vs. world pos
	a.send("G01X20Y30F1000");
	0.2.wait;
	a.state; // send first request
}
)


a.send("G01X00Y0")

a.send("G01X-120Y00F1000")
a.state
a.commands
a.settings
a.reset
a.home;
a.unlock
a.send("$X")

a.pause

a.close



//from serial monitor

// startup

$H - home motor

G90 - set switch to absolute positioning // default

(add to startup block - run after every homing routine - stored in EEPROM)
G92 Xxx Yyy - set work position
G0 X0 Y0 - fast seek to center of workspace coordinate system

optionally:
run into max limit switch, ask where it is (in mm), divide  set steps per mm

G91 - set to relative

// 1.8 degrees per step, gearing: 50/1.  Geared stepper is 50 steps to go 1.8 degrees.
// 1/16th microstepping (specified in motor hardware driver), so actually 50 * 16 steps to go 1.8 degrees.
// For steps/degree(mm) 50*16 / 1.8 = 444.4444... steps per degree

The steps/mm can then be calculated like this: steps_per_mm = (steps_per_revolution*microsteps)/mm_per_rev

(200*16/360)

$100 = 444.444

Serial carriage return ending

$ - show commands
$$ -config params
? - show current state


default is absolute mode

! - pause
~ - resume
ctl + x - pause but hold position
$x - unlock
$h - homing routine


// character counting
l = List[1,2,3,4]
l.removeAt(0)
l.add(rand(22))

l.sum
127 / "G01X120Y-120F560".size


don't send more than 16 line instructions ahead of where it currently is
i.e. steps can't be so small that you queue up more than


/*
[[[[[[[[[[[[[[[[[[[[     FYI     ]]]]]][[[[[[[[[[[[[[[

// $23 - Homing dir invert mask, int:binary

By default, Grbl assumes your homing limit switches are in the positive direction, first moving the z-axis positive, then the x-y axes positive before trying to precisely locate machine zero by going back and forth slowly around the switch. If your machine has a limit switch in the negative direction, the homing direction mask can invert the axes direction. It works just like the step port invert and direction port invert masks, where all you have to do send the value in the table to indicate what axes you want to invert and search for in the opposite direction.

// Re: after homing

TIP: After running a homing cycle, rather jogging manually all the time to a position in the middle of your workspace volume. You can set a G28 or G30 pre-defined position to be your post-homing position, closer to where you'll be machining. To set these, you'll first need to jog your machine to where you would want it to move to after homing. Type G28.1 (or G30.1) to have Grbl store that position. So then after '$H' homing, you could just enter 'G28' (or 'G30') and it'll move there auto-magically.


// RE: feed rate

Feed rate given in millimeters/second
unless invert_feed_rate is true. Then the feed_rate means that the motion should be completed in
(1 minute)/feed_rate time.

// $3 – Direction port invert mask:binary

This setting inverts the direction signal for each axis. By default, Grbl assumes that the axes move in a positive direction when the direction pin signal is low, and a negative direction when the pin is high. Often, axes don't move this way with some machines. This setting will invert the direction pin signal for those axes that move the opposite way.

This invert mask setting works exactly like the step port invert mask and stores which axes to invert as bit flags. To configure this setting, you simply need to send the value for the axes you want to invert. Use the table above. For example, if want to invert the Y axis direction only, you'd send $3=2 to Grbl and the setting should now read $3=2 (dir port invert mask:00000010)
*/