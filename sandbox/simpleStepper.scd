/*
Open questions

why does it stop in the middle of some  paths? .. at high speeds and long paths
...and does it send a message when it stops? how do we count where it is if it stops?

what do the serial messages mean?

how to get the 0/1 that pd got when it stops/moves?

*/


//140909 pan/tilt WorkInProgress
//download FTDI drivers from "http://www.ftdichip.com/Drivers/VCP.htm"
//current firmware is 140902_Firmware_PanTilt.ino

SerialPort.devices

(
p = SerialPort(
	"/dev/tty.usbserial-A9KBFHXP",
	baudrate: 19200,
	crtscts: true);
p.doneAction = { "my serial port got closed".warn; }
)

p.read.postln // blocking
p.next.postln // non-blocking

fork{p.read.postln};

(
~store = [];
r = Routine({
	var noList;
	noList = [111,107,13,10,48,13,10,49,48, 50,51,52];

	inf.do{
		var val;
		val = p.next;
		~store = ~store.add(val);
		// noList.includes(val).not.if{val.postln};
		val.postln;
		0.2.wait;
	}
}).play
)

p.putAll("G01X80Y00F2000") //small ccw(-) pan

r.stop;
~store



(
r = Routine({
	var noList, store;
	noList = [111,107,13,10,48,13,10,49,48, 50,51,52];

	inf.do{
		var val;
		val = p.read;
		noList.includes(val).not.if{val.postln};
		0.1.wait;
	}
})
)
r.reset.play
r.stop

p.putAll("G01X30Y00F5000") //small cw(+) pan
p.putAll("G01X80Y00F500") //small ccw(-) pan

120 * 4

// ~move = { | x, y, speed |
// 	var gotox, gotoy, speedto, msg;
// 	gotox = x.notNil.if({ x.asString.padLeft(2, "0") },{ "00" });
// 	gotoy = x.notNil.if({ x.asString.padLeft(2, "0") },{ "00" });
// 	speedto = ;
// 	msg = format("G01X%Y%F% );
// 		p.putAll("G01X-10Y00F5000") //small pan
// 		}


//change from relative (incremental) to absolute coordinate system INCOMPLETE

p.putAll("G01X01Y00F1000") //small + pan to clear endstop
p.putAll("G01X-360Y00F1000") //long - pan to MIN endstop

p.putAll("G01X00Y01F1000") //small + tilt to clear endstop
p.putAll("G01X00Y-100F1000") //long- tilt to MIN endstop

p.putAll("G01X00Y90F1000") //tilt to known center point

//

p.close

//read 10bit serial data sent from Arduino's Serial.println
(
	r= Routine({
		    var byte, str, res;
		    999.do{|i|
			        if(p.read==115, {
				            str = "";
				            while({byte = p.read; byte !=13 }, {
					                str= str++byte.asAscii;
				            });
				            res= str.asInteger;
				            ("read value:"+res).postln;
			        });
		    };
	"done.postln;"
	}).play;
)

r.stop