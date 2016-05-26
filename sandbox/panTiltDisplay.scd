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
	var minDim, r, d, cen, arcH;
	var arrHeight;
	var circleViewRatio, maxMinStr, dirPnt, azLineClr;
	var azPnt, drawPnt, omniRad, omniDiam, diam, gainColor, gainPnt;

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

	// draw history trace
	azPast.size.do{ |i|
		var color, pnt;
		color = traceColor.alpha_( (1 - ((i+1)/(azPast.size))) * 0.5);

		pnt = Polar(
			// el.linlin(0, 180, -1, 1) for no perspective shift
			sin( elPast[i].degrad ).neg,
			azPast[i].neg.degrad
		).asPoint
		.rotate(0.5pi)	// convert ambi to screen coords
		* arcH;			// scale normalized points to arcH
		Pen.fillColor_( color );
		Pen.fillOval( Rect(pnt.x-r, pnt.y-r, d, d) );

	};

	// trajectory direction point
	dirPnt = Polar(
		sin( el.degrad ).neg,
		az.neg.degrad
	).asPoint
	.rotate(0.5pi)	// convert ambi to screen coords
	* arcH;			// scale normalized points to arcH

	// draw azimuth point w/o perspective
	azPnt = Polar(-1, az.neg.degrad).asPoint.rotate(0.5pi) * arcH;
	Pen.fillColor_(Color.yellow);
	Pen.fillOval( Rect(azPnt.x-r, azPnt.y-r, d, d) );

	// now with perspective
	Pen.fillColor_(Color.magenta);
	Pen.fillOval( Rect(dirPnt.x-r, dirPnt.y-r, d, d) );

	// line to trajectory point
	Pen.strokeColor_(Color.magenta);
	Pen.line(dirPnt, 0@0).stroke;

	// draw "array" with outline
	Pen.rotate(az.neg.degrad);
	arrHeight = diam * ((90-el.abs) / 90);

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
	Pen.rotate(az.neg.degrad);



	/*azLineClr = Color.gray.alpha_(0.2);// line fron center to point

	// get sort order by directivity to draw most transparent first
	aeds_sortOrder = aeds.collect({|arr| arr[2]}).order;
	aeds_sortOrder.do{|sortDex, i|

		#az, el, dir, gain = aeds[sortDex];

		(gain > gainThresh).if{
			omniDiam =	1 - dir * diam;
			omniDiam =	omniDiam.clip(d, diam);
			omniRad	=	omniDiam/2;

			gainColor = getColor.(gain);

			// cartesian point in view coordinates
			azPnt = Point(cos(az), sin(az)) // = Polar(dir, az).asPoint
			.rotate(pi/2)	// convert ambi to screen coords
			* Point(1,-1)	// flip Y for drawing
			* arcH;			// scale normalized points to arcH
			drawPnt = azPnt * dir;

			// line fron center to point
			Pen.strokeColor_(azLineClr);
			Pen.line(drawPnt, 0@0).stroke;

			// directivity circle

			switch(dirDisplay,
				'size',{
					Pen.fillColor_(gainColor.alpha_(alphaSpec.map(dir)));
					Pen.fillOval( Rect(drawPnt.x-omniRad, drawPnt.y-omniRad, omniDiam, omniDiam) );
					gainPnt = azPnt * 1.15; // scale in/out toward/away from origin
				},
				'radius', {
					Pen.fillColor_(gainColor);
					Pen.fillOval( Rect(drawPnt.x-r, drawPnt.y-r, d, d) );
					gainPnt = drawPnt * dir.linlin(0,1,1.75,1.15); // scale in/out toward/away from origin
				}
			);

			// gain labels
			Pen.fillColor_(gainColor.alpha_(1));
			QPen.stringCenteredIn(
				gain.round(0.1).asString,
				Rect(gainPnt.x-(r*10), gainPnt.y-(r*10), d*10, d*10)
			);
		}
	};

	// original 0 deg azimuth point circle ---
	#az, el, dir, gain = aeds[0];
	omniDiam = 1 - dir * diam;
	omniDiam = omniDiam.clip(d, diam);
	omniRad= omniDiam/2;
	azPnt = Point(cos(az), sin(az)) // = Polar(dir, az).asPoint
	.rotate(pi/2)	// convert ambi to screen coords
	* Point(1,-1)	// flip Y for drawing
	* arcH;			// scale normalized points to arcH
	drawPnt = azPnt * dir;

	Pen.strokeColor = Color.fromHexString("#CC0000");
	Pen.width = 3;
	Pen.strokeOval( Rect(drawPnt.x-r, drawPnt.y-r, d, d) );*/

});



win.refresh;

~readAzEl !? {~readAzEl.stop};

~readAzEl = Routine.run({
	inf.do{ |i|
		el = ~el;
		az = ~az;

		elPast = elPast.rotate(1);
		elPast[0] = el;

		azPast = azPast.rotate(1);
		azPast[0] = az;

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