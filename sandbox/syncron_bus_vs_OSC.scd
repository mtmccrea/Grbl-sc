(
c = Bus.control(s,1);

// source signal written to a bus
SynthDef(\YequalX, { Out.kr(c, LFTri.kr(0.25, mul:1)) }).add;

f = { |latency = 0.2, delta = 0.01, num = 100|
	var a = List(), d = List(), v,

	x = (instrument: \YequalX, latency: latency).play;

	Routine {
		num.do { |i|
			var t, n;
			t = Main.elapsedTime;
			{v = c.getSynchronous;
			n = thisThread.clock.seconds;
			d.add(t - n);

				a.add(v);}.fork;
			((num - 1) == i).if { x.free  };
			delta.wait;
		}
	}.play;
	[a, d]
}
)
b = f.(0.0, 689.0625.reciprocal, 689.0625)
b = f.(0.0, 100.reciprocal, 100)
b = f.(0.0, 10.reciprocal, 10)
b[0].as(Array).plot
b[1].asArray.plot
/*
(
c = Bus.control(s,1);

SynthDef(\YequalX, {
	|freq=100, dur=1|
	SendReply.ar(
		Impulse.ar(freq),
		'/busser',
		LFTri.kr(0.25));
	Line.kr(0,1,dur,doneAction:2) }
).add;

f = { |latency = 0.2, delta = 0.01, num = 100|
	a = List();
	d = List();

	OSCdef(\busWhisperer, { |msg, time, addr, recvPort|
var t, n;
			t = Main.elapsedTime;
		v = msg[3];
			n = thisThread.clock.seconds;
			d.add(t - n);

			a.add(v);
	}, '/busser');

	x = (instrument: \YequalX, freq: 1/delta, dur: num*delta, latency: latency).play;
}
)

// run, wait one second
f.(0.2, 0.01, 300);
// wait a second...
b = [a, d]
b[0].as(Array).plot /// But wait... this is the value written to the bus, not read from it, doh!
*/

(
c = Bus.control(s,1);

// source signal written to a bus
SynthDef(\YequalX, {|dur=1| Out.kr(c, LFTri.kr(0.25)); Line.kr(0,1,dur,doneAction:2); }).add;

// synth to read the source signal
SynthDef(\readIt, {
	|freq=100, dur=1|
	SendReply.ar(
		Impulse.ar(freq),
		'/busser',
		In.kr(c,1) // read in the bus that LFTri writes to, send it over OSC to lang
	);
	Line.kr(0,1,dur,doneAction:2) }
).add;

f = { |latency = 0.2, delta = 0.01, num = 100|
	~a = List();
	~d = List();

	OSCdef(\busWhisperer, { |msg, time, addr, recvPort|
		var t, n, v;
		t = Main.elapsedTime;
		n = thisThread.clock.seconds;
		v = msg[3]; // bus value

		~d.add(t - n);

		~a.add(v);
	}, '/busser');

	x = (instrument: \readIt, freq: 1/delta, dur: num*delta, latency: latency).play; // read first
	y = (instrument: \YequalX,  dur: num*delta, latency: latency).play;					// then play source
}
)

// run, wait one second
f.(0.2, 689.0625.reciprocal, 689.0625); //kr
f.(0.2, 0.01, 100);
// wait a second...
b = [~a, e]
b[0].as(Array).plot /// Looks better than getSyncronous! Right? But it's a pain :(

( // clean stuff up
a=b=nil;
~a=~d=nil;
OSCdef(\busWhisperer).free;
c.free;
)