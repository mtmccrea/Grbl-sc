(
var win, uv, az=0, el=0, elPast, azPast;
var fr, historySecs, traceColor;

fr = 10;
historySecs = 5;
traceColor = Color.gray;

elPast = Array.fill(fr * historySecs, {0});
azPast = Array.fill(fr * historySecs, {0});

~az = az;
~el = el;
~azOscFreq = 15.reciprocal;
~elOscFreq = 8.reciprocal;
~azOscMin = -180;
~azOscMax = 180;
~elOscMin = 0;
~elOscMax = 180;


win = Window().front;
uv = UserView(win, Rect(width:win.bounds.width, height: win.bounds.height)).animate_(true).frameRate_(fr).resize_(5);

uv.drawFunc_({|view|
	var azNegRad, elRad, minDim, r, d, cen, arcH;
	var arrHeight;
	var circleViewRatio, maxMinStr, dirPnt, azLineClr;
	var azPnt, drawPnt, omniRad, omniDiam, diam, gainColor, gainPnt;

	azNegRad = az.neg.degrad; // optimize for drawing coords
	elRad = el.degrad;

	minDim = [uv.bounds.width, uv.bounds.height].minItem;

	r = minDim/2 * 0.02;
	d = r*2;
	circleViewRatio = 0.8;
	arcH = minDim * circleViewRatio / 2;	// height of the "fan" arc
	diam = 2 * arcH;

	cen = view.bounds.center; // center drawing origin

	Pen.translate(cen.x, cen.y);

	Pen.addAnnularWedge( 0@0, 5, arcH, 0, 2pi );
	Pen.fillColor_(Color.gray(0.9)).fill;

	// background circles
	Pen.strokeColor_(Color.gray.alpha_(0.2));
	3.do{|i|
		var val;
		val = (i+1 / 3);
		Pen.strokeOval( Rect(
			(arcH * val).neg, (arcH * val).neg,
			diam*val, diam*val
		));
	};

	// // draw history trace
	// azPast.size.do{ |i|
	// 	var color, pnt;
	// 	color = traceColor.alpha_( (1 - ((i+1)/(azPast.size))) * 0.5);
	//
	// 	pnt = Polar(
	// 		// el.linlin(0, 180, -1, 1) for no perspective shift
	// 		sin( elPast[i] ).neg,
	// 		azPast[i]
	// 	).asPoint
	// 	.rotate(0.5pi)	// convert ambi to screen coords
	// 	* arcH;			// scale normalized points to arcH
	// 	Pen.fillColor_( color );
	// 	Pen.fillOval( Rect(pnt.x-r, pnt.y-r, d, d) );
	//
	// };

	// draw history trace
	azPast.size.do{ |i|
		var color, pnt;
		Pen.push;
		color = traceColor.alpha_( (1 - ((i+1)/(azPast.size))) * 0.5);
		Pen.fillColor_( color );
		Pen.rotate(azPast[i]);
		Pen.fillOval( Rect(r.neg, sin(elPast[i]).neg * arcH-r, d, d) );
		Pen.pop;
	};


	Pen.rotate(azNegRad);
	// draw "array" with outline
	// Pen.rotate(azNegRad);
	arrHeight = diam * ((0.5pi-elRad.abs) / (0.5pi));

	Pen.fillColor_(Color.blue.alpha_(0.1));
	Pen.fillOval( Rect(
		0 - arcH,
		0 - (arrHeight / 2),
		diam, arrHeight)
	);

	Pen.strokeColor_(Color.magenta);
	Pen.strokeOval( Rect(
		0 - arcH,
		0 - (arrHeight / 2),
		diam, arrHeight)
	);

	// trajectory direction point
	dirPnt = Polar(
		sin( elRad ).neg,
		0/*azNegRad*/
	).asPoint
	.rotate(0.5pi)	// convert ambi to screen coords
	* arcH;			// scale normalized points to arcH

	// draw azimuth point w/o perspective
	azPnt = Polar(-1, 0/*azNegRad*/).asPoint.rotate(0.5pi) * arcH;
	Pen.fillColor_(Color.yellow);
	Pen.fillOval( Rect(azPnt.x-r, azPnt.y-r, d, d) );

	// line to trajectory point
	Pen.strokeColor_(Color.green);
	Pen.line(dirPnt, 0@0).stroke;

	// draw azimuth with perspective
	Pen.fillColor_(Color.red);
	Pen.fillOval( Rect(dirPnt.x-r, dirPnt.y-r, d, d) );


	// Pen.rotate(azNegRad);

});



win.refresh;

~readAzEl !? {~readAzEl.stop};

~readAzEl = Routine.run({
	inf.do{ |i|
		el = ~el;
		az = ~az;

		elPast = elPast.rotate(1);
		elPast[0] = el.degrad; // optimize, store in radians

		azPast = azPast.rotate(1);
		azPast[0] = az.neg.degrad; //optimize: store .neg.degrad instead of az;

		fr.reciprocal.wait;
	}
});


// simulate a moving array with oscilating az's and el's
~writeAzEl = Routine.run({
	var cnt_az = 0, cnt_el = 0, dex_az, dex_el, fr = 10;
	inf.do{ |i|
		var cntsPerCycle_az, cntsPerCycle_el;

		cntsPerCycle_az = fr/~azOscFreq;
		cntsPerCycle_el = fr/~elOscFreq;

		dex_az = cnt_az / cntsPerCycle_az * 2pi;
		dex_el = cnt_el / cntsPerCycle_el * 2pi;

		~el = sin(dex_el).linlin(-1,1,~elOscMin, ~elOscMax);
		~el.postln;

		~az = sin(dex_az).linlin(-1,1,~azOscMin, ~azOscMax);
		~az.postln;

		cnt_az = cnt_az + 1;
		cnt_az = cnt_az % cntsPerCycle_az;

		cnt_el = cnt_el + 1;
		cnt_el = cnt_el % cntsPerCycle_el;

		fr.reciprocal.wait;
	}
});


)
/*
~readAzEl.stop;
~readAzEl.reset.play;

~readAzEl.isPlaying

~writeAzEl.stop;
~writeAzEl.reset.play;

~el = -10
~az = 30

*/