/*
   !!! NOTE !!!
   This class is deprecated and was refactored into
   GrblDriver and extGrblDriver_LFO
*/

MotorDriver {
	// copyArgs
	var <grbl, <cyclePeriod, <xLo, <xHi, <yLo, <yHi, <numIntervals, <stateRefreshRate, <initDriveMode;

	var
	<>debug = false, <driveRoutine, feedsRates, <feeds, <motorRates, <feedRateEnv,
	<initialized, <started, <origin, <destination, <feed, <degPerSec,
	intervalSizes, travelDistances, travelDistsAbs,
	checkPointsOffsets, checkPoints, nextCheckPoints, checkPointDex, scheduleDex,
	directions, nextDirections, maxTravelAbs, maxTravelAxisDex, maxTravelSigned,
	intervalDestinations, nextIntervalDestinations, <lastScheduled,
	test, <timeoutClock, <destTimeEstimate, trackedDex,
	<periodicStepCount, <slowAxis, <numBasePeriodDivs, <driveMode, <basePeriod,
	<centerX, <centerY, <rangeX, <rangeY, <gui, <driving, <randPeriod, <motorMaxRate;
	// X/Y driving
	var
	lfoDrivingEnabled = false, <lfoControlX, <lfoControlY,
	<plotterX, <plotterY, <lastUpdated, updateButton, presetWin ;


	*new {
		arg aGrbl, cyclePeriod = 4,
		xLow = -45, xHigh = 45, yLow = 0, yHigh = 45,
		numIntervals = 4, stateRefreshRate = 8, initDriveMode = 'lfo';

		^super.newCopyArgs(
			aGrbl, cyclePeriod, xLow, xHigh, yLow, yHigh, numIntervals, stateRefreshRate, initDriveMode
		).init;
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

		basePeriod = 12;
		numBasePeriodDivs = 2;
		randPeriod = cyclePeriod;
		centerX = 0;
		centerY = 20;
		rangeX	= 60;
		rangeY	= 30;
		driving	= false;
		slowAxis = 0; // default slow axis to pan

		// NOTE: feedRates are only used by this.driveRandom and this.drivePeriodic
		// driveLFO simply uses a controlspec from minFeed>maxFeed

		// TODO: move this elsewhere
		/* PAN/TILT */
		// feedsRates = [
		// 	// feerate, deg/sec
		// 	50, 0.0, 				// initial value
		// 	50, 0.83407611854643,
		// 	150, 2.4999954670916,
		// 	250, 4.16358086331,
		// 	500, 8.2842227651998,
		// 	1000, 16.375337724438,
		// 	1500, 23.886587679215,
		// 	2000, 31.077416235938,
		// 	2500, 37.128224866405,
		// 	3000, 43.947974732258,
		// 	4000, 52.819528420555,
		// 	4500, 56.25322886502,
		// 	5000, 58.596330050585,
		// 	5200, 60.976640482347,
		// 	// 5400, 60.809889561544,
		// ].clump(2);

		/* DRAWING MACHINE */
		feedsRates = [
			// feerate, deg/sec
			50, 0.0, 				// initial value
			50, 0.58933998530736,
			100, 1.1784864088791,
			150, 1.766332510846,
			200, 2.3554466489987,
			250, 2.9387824931351,
			300, 3.5309292673395,
			350, 4.1161539641257,
			400, 4.70488788577,
			450, 5.2739072488243,
			500, 5.8500539952823,
			1000, 11.644196548778,
			1500, 17.357342433294,
			2000, 23.000702940588,
			2500, 27.977127739391,
			3000, 34.072662483228,
			3500, 38.641830392726,
			4000, 44.092443007887,
			4500, 48.044316538766,
			5000, 51.330588213943,
			5500, 54.315492723538,
			6000, 60.446681695289,
			6500, 62.428089084138,
		].clump(2);

		feeds = feedsRates.collect{|fs| fs[0]};
		motorRates = feedsRates.collect{|fs| fs[1]};
		feedRateEnv = Env( feeds, motorRates.differentiate.drop(1) );

		motorMaxRate = motorRates.maxItem;

		// update bounds
		this.centerX_(centerX, rangeX);
		this.centerY_(centerY, rangeY);

		{
			var cond = Condition();

			switch( initDriveMode,
				'random',	{this.driveRandom},
				'periodic',	{this.drivePeriodic},
				'lfo', 		{
					this.initLfoDriving( cond );
					cond.wait;
				}
			);

			driveMode ?? { "invalid drive mode".error }; // in case invalid initDriveMode given

			// make sure the motor position state is being updated
			grbl.updateState_(true, stateRefreshRate, false);

			if( initDriveMode != 'lfo',
				{
					initialized = false;
					started = false;
					timeoutClock = TempoClock();

					gui = MotorDriverView(this, grbl)

				},{
					gui = MotorDriverLfoView(this, grbl)
				}
			);
		}.fork(AppClock);
	}

	getBounds { |whichAxis|
		whichAxis.notNil.if(
			{ ^[[xLo, xHi],[yLo, yHi]].at(whichAxis) },
			{ ^[[xLo, xHi],[yLo, yHi]] }
		);
	}

	bounds_ { |xLow, xHigh, yLow, yHigh|
		var lo, hi, center, range;

		if( xLow.notNil or: xHigh.notNil, {
			hi = xHigh ?? xHi;
			lo = xLow ?? xLo;
			center = (lo + hi).half;
			range = hi - lo;
			// forward through center and range setters
			// for limit checks and .changed dispatches
			this.centerX_( center, range );
		});

		if( yLow.notNil or: yHigh.notNil, {
			hi = yHigh ?? yHi;
			lo = yLow ?? yLo;
			center = (lo + hi).half;
			range = hi - lo;
			// forward through center and range setters
			// for limit checks and .changed dispatches
			this.centerY_( center, range );
		});
	}

	centerX_ { |centerDegree, newRangeX|
		centerDegree ?? {"No center specified".error};

		centerX = centerDegree.clip(grbl.xLimitLow+1, grbl.xLimitHigh-1);
		// re-calculate the X range and set xLo/xHi
		this.rangeX_(newRangeX ?? rangeX);
		this.changed(\centerX, centerX);
	}

	centerY_ { |centerDegree, newRangeY|
		centerDegree ?? {"No center specified".error};

		centerY = centerDegree.clip(grbl.yLimitLow+1, grbl.yLimitHigh-1);
		// re-calculate the Y range and set yLo/yHi
		this.rangeY_(newRangeY ?? rangeY);
		this.changed(\centerY, centerY);
	}

	rangeX_ { |rangeDegree|
		var lo, hi, clipped = false;
		rangeDegree ?? {"No range specified".error};
		lo = centerX - rangeDegree.half;
		hi = centerX + rangeDegree.half;
		clipped = [lo, hi].collect({|me|
			me.inRange(grbl.xLimitLow+1, grbl.xLimitHigh-1).not
		}).includes(true);
		#lo, hi = [lo, hi].clip(grbl.xLimitLow+1, grbl.xLimitHigh-1); // +/-2 just in case

		// hold center position, don't allow range to grow if one bound hits a limit
		clipped.if{
			var halfRange;
			halfRange = min( (hi - centerX).abs, (centerX - lo).abs );
			lo = centerX - halfRange;
			hi = centerX + halfRange;
		};

		xLo = lo;
		xHi = hi;
		rangeX = xHi - xLo;
		this.changed( \rangeX, rangeX );

		clipped.if{
			var msg;
			msg = "Clipping requested X range";
			warn(msg);
			this.changed(\status, msg);
		};

		switch( driveMode,
			'random', 	{ this.checkRateByRandPeriod },
			'periodic', { this.checkRateByBaseAxisPeriod }
		);
	}

	rangeY_ { |rangeDegree|
		var lo, hi, clipped = false;
		rangeDegree ?? {"No range specified".error};

		lo = centerY - rangeDegree.half;
		hi = centerY + rangeDegree.half;
		clipped = [lo, hi].collect({|me|
			me.inRange(grbl.yLimitLow+1, grbl.yLimitHigh-1).not
		}).includes(true);

		#lo, hi = [lo, hi].clip(grbl.yLimitLow+1, grbl.yLimitHigh-1); // +/-2 just in case

		// hold center position, don't allow range to grow if one bound hits a limit
		clipped.if{
			var halfRange;
			halfRange = min( (hi - centerY).abs, (centerY - lo).abs );
			lo = centerY - halfRange;
			hi = centerY + halfRange;
		};

		yLo = lo;
		yHi = hi;
		rangeY = yHi - yLo;
		this.changed( \rangeY, yHi - yLo );

		clipped.if{
			var msg;
			msg = "Clipping requested Y range";
			warn(msg);
			this.changed(\status, msg);
		};

		switch( driveMode,
			'random', 	{ this.checkRateByRandPeriod },
			'periodic', { this.checkRateByBaseAxisPeriod }
		);
	}


	initLfoDriving { |finishedCond|
		var cond = Condition();
		driveMode = 'lfo';
		fork {
			lfoDrivingEnabled.not.if{

				grbl.writePosToBus_(true, completeCondition: cond);
				cond.wait; cond.test_(false);

				lfoControlX = ControlFade( 8, onComplete: {cond.test_(true).signal});
				cond.wait; cond.test_(false);
				lfoControlX.lfoSynths.do{|synth| synth.freq_(20.reciprocal)};
				lfoControlX.lfoBounds_(xLo, xHi, 0);

				lfoControlY = ControlFade( 8, onComplete: {cond.test_(true).signal});
				cond.wait;
				lfoControlY.lfoSynths.do{|synth| synth.freq_(20.reciprocal)};
				lfoControlY.lfoBounds_(yLo, yHi, 0);

				// initialize driving controls to wPos
				lfoControlX.value_( grbl.wPos[0], 0 );
				lfoControlY.value_( grbl.wPos[1], 0 );

				lfoDrivingEnabled = true;
				finishedCond !? {finishedCond.test_(true).signal};
			}
		}
	}


	plotDriver { |withMotorPos = true|
		lfoDrivingEnabled.if({

			plotterX = ControlPlotter(
				[lfoControlX.busnum, grbl.posBus.bus], 2,
				plotLength: 35, refresh_rate: 10, plotMode: 'linear',
			).start.bounds_(grbl.xLimitLow,grbl.xLimitHigh)
			.plotColors_([Color.black, Color.red]);

			plotterY = ControlPlotter(
				[lfoControlY.busnum, grbl.posBus.bus+1], 2,
				plotLength: 35, refresh_rate: 10, plotMode: 'linear',
			).start.bounds_(grbl.yLimitLow,grbl.yLimitHigh)
			.plotColors_([Color.black, Color.blue]);
			},{
				"lfo driving not enabled".warn;
		})
	}


	driveLfo {
		if( lfoDrivingEnabled, {
			grbl.followSerialBufXY_(
				lfoControlX.controlBus, lfoControlY.controlBus,
				grbl.motorInstructionRate ?? 40,
				grbl.stateUpdateRate ?? 8
			);
			driving = true;
			this.changed(\driving, driving);
			},{ "lfo driving not enabled".warn }
		)
	}

	stopLfo {
		grbl.unfollow;
		driving = false;
		this.changed(\driving, driving);
	}


	storePreset { | key, overwrite =false |
		var arch, cx, cy;

		arch = Archive.global[\motorDriverStates] ?? { this.prInitArchive };

		block { |break|
			(arch[key].notNil and: overwrite.not).if { format(
				"preset already exists! choose another name or first perform .removePreset(%)", key
			).error; break.() };

			#cx, cy = [lfoControlX, lfoControlY];

			arch.put( key.asSymbol ?? {Date.getDate.stamp.asSymbol},

				IdentityDictionary( know: true ).putPairs([
					\ctlSrcDex,		[cx,cy].collect{ |me| me.mixSynth.ctlSrcDex },
					\staticVal,		[cx,cy].collect{ |me| me.mixSynth.staticVal },

					\lfoParams, IdentityDictionary( know: true ).putPairs([
						\ugen,		[cx,cy].collect{ |me| me.curLfoUgens[me.mixSynth.lfoDex] },
						\freq,		[cx,cy].collect{ |me| me.lfoSynths[me.mixSynth.lfoDex].freq },
						\low,		[cx,cy].collect{ |me| me.lfoSynths[me.mixSynth.lfoDex].low },
						\high,		[cx,cy].collect{ |me| me.lfoSynths[me.mixSynth.lfoDex].high },
					]),
				]);
			);

			lastUpdated = key;
		};
	}

	recallPreset { |key, fadeTime = 12|
		var preset, ctlSrcDex;

		preset = Archive.global[\motorDriverStates][key];
		preset ?? {"motor driver preset not found!".error};
		ctlSrcDex = preset[\ctlSrcDex];

		[lfoControlX, lfoControlY].do{ |ctl, i|
			switch( ctlSrcDex[i],
				// static val
				0, { ctl.value_( preset[\staticVal][i], fadeTime ) },
				1, { var p, ugen, freq, low, high, cen, range;
					p = preset[\lfoParams];
					#ugen, freq, low, high = [p.ugen[i], p.freq[i], p.low[i], p.high[i]];
					ctl.lfo_(ugen, freq, low, high, fadeTime);
					cen = (low + high).half;
					range = (high - low).abs;
					// update instance vars, which updates gui
					switch( i,
						0, { this.centerX_(cen, range) },
						1, { this.centerY_(cen, range) }
					);
					this.changed(\lfo, i, ugen, freq);
				}
			);
			// swith to the new val/lfo
			ctl.source_( preset[\ctlSrcDex][i] );
		};

		lastUpdated = key;
	}

	// which: 'pan' or 'tilt'
	// index: static value: 0, lfo: 1
	lfoSrcDex_ { |which, index|
		index !? {
			switch( which,
				'pan',	{ lfoControlX.source_(index) },
				'tilt',	{ lfoControlY.source_(index) }
			);
		}
	}

	updatePreset {
		lastUpdated.notNil.if({
			this.storePreset( lastUpdated, true );
			},{
			"last updated key is not known".warn
		});
	}

	removePreset { |key|

		Archive.global[\motorDriverStates][key] ?? {
			format("preset % not found!", key).error
		};

		Archive.global[\motorDriverStates].removeAt(\key)
	}

	*archive	{ ^Archive.global[\motorDriverStates] }
	archive		{ ^Archive.global[\motorDriverStates] }

	*presets	{ ^Archive.global[\motorDriverStates].keys.asSortedList }
	presets		{ ^Archive.global[\motorDriverStates].keys.asSortedList }

	*listPresets {
		^Archive.global[\motorDriverStates].keys.asSortedList.do(_.postln)
	}
	listPresets	{
		^Archive.global[\motorDriverStates].keys.asSortedList.do(_.postln)
	}

	presetGUI {
		var presetLayouts = this.presets.asArray.sort.collect{ |name, i|
			var lay;

			lay = HLayout(
				Button()
				.states_([[name]])
				.action_({
					updateButton !? {updateButton.remove};
					this.recallPreset(name.asSymbol);
					lay.add(
						updateButton = Button().states_([["update"]])
						.action_({this.updatePreset})
					)
				})
			)
		};

		presetWin = Window("Movement Presets", Rect(0,0,200, 400)).view.layout_(
			VLayout( *presetLayouts )
		).front;
	}

	*backupPreset {
		Archive.write(format("~/Desktop/archive_MotorDriverBAK_%.sctxar",Date.getDate.stamp).standardizePath)
	}

	backupPreset { this.class.backupPreset	}

	prInitArchive {
		^Archive.global.put(\motorDriverStates, IdentityDictionary(know: true));
	}




	basePeriod_ { |seconds|
		basePeriod = seconds;
		cyclePeriod = basePeriod / numBasePeriodDivs * 0.5;
		if( cyclePeriod < 2, {
			warn("Cycle period may be too short and the motor may fall behind");
			this.changed(\status, "WARNING: Cycle period may be too short and the motor may fall behind.");
			}
		);

		debug.if{("cyclePeriod to "++cyclePeriod).postln};

		this.checkRateByBaseAxisPeriod;

		this.changed( \basePeriod, basePeriod);
		this.changed( \cyclePeriod, cyclePeriod);
	}

	numBasePeriodDivs_ { |int|
		numBasePeriodDivs = int.round;

		// recalculate the base period
		cyclePeriod = basePeriod / numBasePeriodDivs * 0.5;

		this.checkRateByBaseAxisPeriod;

		this.changed( \numBasePeriodDivs, numBasePeriodDivs);
		this.changed( \basePeriod, basePeriod);
		this.changed( \cyclePeriod, cyclePeriod);

		debug.if{("Updating rate multiple to "++int).postln};
	}

	baseAxis_ { |axisDex|

		slowAxis = axisDex;

		// for updating while currently driving in periodic driveMode
		if( (driveMode == 'periodic') and: driving, {
			this.checkRateByBaseAxisPeriod;
			this.changed( \basePeriod, basePeriod);
			this.changed( \cyclePeriod, cyclePeriod);
		});

		this.changed(\baseAxis, slowAxis);
		this.changed(\status, "Swapping base axis to " ++ ["pan.", "tilt."].at(slowAxis));
	}

	randPeriod_ { |seconds|

		if(driveMode == 'random', {
			cyclePeriod = seconds;
			// check rate required and adujust cycle period as needed
			this.checkRateByRandPeriod;
			randPeriod = cyclePeriod;
			this.changed(\cyclePeriod, cyclePeriod);
			}
		);

		// otherwise just set randPeriod, which will be checked against
		// max motor rate when driveMode is called
		randPeriod = seconds;
		this.changed(\randPeriod, randPeriod);
	}

	checkRateByRandPeriod {
		var maxTravel, maxRate;

		// check the range of travel against the cyclePeriod
		// to make sure the cyclePeriod isn't too fast
		maxTravel = [rangeX, rangeY].maxItem;
		maxRate = maxTravel / cyclePeriod;

		case
		{ maxRate > (motorMaxRate * 0.9) } {
			cyclePeriod = maxTravel / (motorMaxRate * 0.9);
			randPeriod = cyclePeriod;
			this.changed( \status,
				"Limiting random period (motor rate) based on requested axis/period settings"
			);
			this.changed( \maxRate, (maxTravel / cyclePeriod) )
		}
		{maxRate > (motorMaxRate * 0.75) } {
			this.changed( \status,
				"CAREFUL:  Approaching max rate, and instability! Reduce ranges, increase rand period"
			);
			this.changed( \maxRate, maxRate );
		}
		{ true } {
			this.changed( \maxRate, maxRate );
		};
	}

	checkRateByBaseAxisPeriod {
		var maxTravel, maxRate;

		// check the range of travel against the cyclePeriod
		// to make sure the cyclePeriod isn't too fast
		// get the fast axis's range - must account for
		// which axis is on the cycle period
		maxTravel = [rangeX, rangeY].at((slowAxis-1).abs);
		maxRate = maxTravel / cyclePeriod;

		case
		// limit more for periodic
		{ maxRate > (motorMaxRate * 0.8) } {
			// limit the cycle period to the time needed to
			// cover maxTravel at motorMaxRate
			cyclePeriod = maxTravel / (motorMaxRate * 0.8);
			// re-calculate basePeriod
			basePeriod = cyclePeriod * numBasePeriodDivs * 2;
			this.changed( \status,
				"Limiting cycle period (motor rate) based on requested axis/period settings!"
			);
			this.changed( \maxRate, (maxTravel / cyclePeriod) )
		}
		{ maxRate > (motorMaxRate * 0.7) } {
			this.changed( \status,
				"CAREFUL: Approaching max rate, and instability! Reduce ranges, increase periods, or decrease rate multiples."
			);
			this.changed( \maxRate, maxRate );
		}
		{ true } {
			this.changed( \maxRate, maxRate );
		};

	}

	driveRandom { |randPeriodSecs|

		// for swapping between random<>periodic on the fly
		if( (driveMode == 'periodic') and: driving, {
			this.stop;
			this.changed(\status, "Changing from periodic to random in 3 seconds.");
			{ 3.wait; this.drive; }.fork(AppClock);
		});

		driveMode = 'random';
		randPeriodSecs !? {randPeriod = randPeriodSecs};
		cyclePeriod = randPeriod;
		this.checkRateByRandPeriod;

		// reset
		initialized = false;	// so re-initializes on next state update
		started = false;		// so sets origin as
		this.changed(\driveMode, driveMode);
		this.changed(\randPeriod, randPeriod);
		this.changed(\cyclePeriod, cyclePeriod);
	}

	drivePeriodic { |basePeriodSecs, baseAxis, basePeriodDivisions|

		// for swapping between random<>periodic on the fly
		if( (driveMode == 'random') and: driving, {
			this.stop;
			this.changed(\status, "Changing from random to periodic in 3 seconds.");
			{ 3.wait; this.drive; }.fork(AppClock);
		});

		// set global vars
		driveMode = 'periodic';
		periodicStepCount = 0;
		baseAxis !? {slowAxis = baseAxis};
		basePeriodDivisions !? {numBasePeriodDivs = basePeriodDivisions};
		basePeriodSecs !? {basePeriod = basePeriodSecs};
		// the new period for each new destination is the half
		// of the basePeriod (time to rise from low bound to high bound)
		// divided by the numBasePeriodDivs
		cyclePeriod = basePeriod / numBasePeriodDivs * 0.5;

		this.checkRateByBaseAxisPeriod;

		// reset
		initialized = false;	// so re-initializes on next state update
		started = false;		// so sets origin as
		this.changed(\driveMode, driveMode);
		this.changed(\baseAxis, slowAxis);
		this.changed(\cyclePeriod, cyclePeriod);
	}

	chooseRandDest {
		^[ rrand(xLo, xHi).round(0.1), rrand(yLo, yHi).round(0.1) ]
	}

	choosePeriodicDest {
		var slowDex, fastDex, slowDest, fastDest, destsOrdered;

		periodicStepCount = periodicStepCount + 1;
		// climbs and descends ramp
		slowDex = periodicStepCount.fold(0, numBasePeriodDivs);
		// toggle 0/1
		fastDex = periodicStepCount % 2;
		// reset counter
		(slowDex == 0).if{ periodicStepCount = 0 };

		postf("periodicStepCount %\tslowDex %\tfastDex %\n",
			periodicStepCount, slowDex, fastDex);

		slowDest = slowDex.linlin(0, numBasePeriodDivs, *this.getBounds(slowAxis));
		fastDest = this.getBounds((slowAxis-1).abs).at(fastDex);

		^destsOrdered = [slowDest, fastDest].rotate(slowAxis);
	}

	prepareDestination {
		var trycount = 0;

		// init to 0 to get into the while loop
		maxTravelAbs = 0;

		if(driveMode == 'periodic', {periodicStepCount = periodicStepCount + 1});

		block{ |break|
			while ( { maxTravelAbs < 1.5 }, // force it to travel at least this amount (deg)
				{
					debug.if{
						"in while".postln;
						postf( "lastScheduled % ?? grbl.wPos[0..1] % ?? [0,0]\n",
							lastScheduled, grbl.wPos[0..1]);
					};
					// origin of the move, which is the last destination,
					// unless the process hasn't yet been initialized
					origin = if( initialized.not,
						// in the case of timing out, initialized is false
						// so it first tries to start from the lastScheduled move
						{ lastScheduled ?? grbl.wPos[0..1] ?? [0,0]},
						{ destination }
					);

					// the randomly chosen destination
					destination =
					switch( driveMode,
						'periodic',	{
							// this will be adde back when choosePeriodicDest is successful
							periodicStepCount = periodicStepCount - 1;
							this.choosePeriodicDest
						},
						'random',	{ this.chooseRandDest }
					);

					// find which travel distance is greater, x or y
					travelDistances		= destination - origin;
					travelDistsAbs		= travelDistances.abs;
					maxTravelAbs		= travelDistsAbs.maxItem;	// the distance to track, abs
					maxTravelAxisDex	= travelDistsAbs.maxIndex;	// 0 if x, 1 if y

					postf("origin %\ndestination %\ntravelDistsAbs %\ntravelDistances %\nmaxTravelAbs %\nmaxTravelAxisDex %\n",
						origin, destination, travelDistsAbs, travelDistances, maxTravelAbs, maxTravelAxisDex);

					(trycount > 5).if{
						"forcing a destination after 5 tries".warn;
						destination = [[xLo, yLo], [xHi, yHi]].sum.collect(_.half).round(0.1); // split take the middle of hi/lo
						travelDistances		= destination - origin;
						travelDistsAbs		= travelDistances.abs;
						maxTravelAbs		= travelDistsAbs.maxItem;	// the distance to track, abs
						maxTravelAxisDex	= travelDistsAbs.maxIndex;	// 0 if x, 1 if y
						break.("BREAKING OUT".postln;)
					};

					trycount = trycount + 1;
			});
		};
	}

	// choose a new destination based on frequency and distance covered
	initNextMove {

		this.prepareDestination;

		// calc feed speed, just use the feed for the axis that travels farthest
		degPerSec = maxTravelAbs / cyclePeriod;
		feed = feedRateEnv.at(degPerSec).round(1);

		intervalSizes = travelDistsAbs / numIntervals;
		checkPointsOffsets = intervalSizes * 0.25;

		// make estimate slightly longer to account for variance,
		// as it's used as a fail-safe if a destination isn't achieved.
		// note: timeout is scheduled after the first checkpoint is crossed (checkPointsOffset)
		// note: this only applies to the distance that's tracked (maxTravel)
		// TODO: reign in the sloppyness allowed by the long timeout estimate
		destTimeEstimate = (maxTravelAbs - checkPointsOffsets[maxTravelAxisDex]) * 2 / degPerSec;

		debug.if{
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
	}

	initTravel {
		debug.if{"INITIALIZING".postln};
		checkPointDex = 0;
		periodicStepCount = 0;

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

		debug.if{ // debug
			"\tcheckpoints".postln; checkPoints.do(_.postln);
			"\tintervalDestinations".postln; intervalDestinations.do(_.postln);
		};
		// just beginning - schedule the first 2 intervals
		2.do{ |i|
			grbl.goTo_(
				intervalDestinations[i][0],
				intervalDestinations[i][1],
				feed
			);
			// debug
			postf("\tScheduling % at %\n", intervalDestinations[i], feed);
		};

		scheduleDex = 2;
		initialized = true;
	}

	// at the rate of the motor position update:
	drive {
		driving = true;
		periodicStepCount = 0;
		this.changed(\driving, driving);

		driveRoutine.notNil.if(
			{
				driveRoutine.isPlaying.not.if{
					//timeoutClock.clear; // clear the former timeout
					driveRoutine.reset.play;
				}
			},{
				driveRoutine = Routine.run({

					inf.do{

						// set initial state
						if( initialized.not, { this.initTravel });

						test = if( directions[trackedDex].isPositive,
							{grbl.wPos[trackedDex] > checkPoints[checkPointDex][trackedDex]},
							{grbl.wPos[trackedDex] < checkPoints[checkPointDex][trackedDex]}
						);

						debug.if{ postf("test % % than %\t%\n",
							grbl.wPos[trackedDex], if(directions[trackedDex].isPositive, {"greater"},{"less"}),
							checkPoints[checkPointDex][trackedDex], test
						)};

						if( test,
							// has it passed a checkpoint?
							{
								started = true;

								case
								{ checkPointDex < (numIntervals - 2) } {
									// schedule the next interval (2 ahead of current)
									grbl.goTo_(intervalDestinations[scheduleDex][0], intervalDestinations[scheduleDex][1], feed);
									lastScheduled = intervalDestinations[scheduleDex];
									postf("\tScheduling % at %\n", intervalDestinations[scheduleDex], feed);

									if( checkPointDex == 0, {
										timeoutClock.clear; // clear the former timeout
										timeoutClock.sched( destTimeEstimate, {
											initialized = false;	// so re-initializes on next state update
											started = false;		// so sets origin as current work position
											driving.if{
												"\nTIMED OUT - reinitializing\n".warn;
												defer {
													this.changed(\status,
														"WARNING: Travel timed out, likely moving too fast.") };
											};
											}
										);
									})
								}

								// second-to-last interval indexes before the destination
								{ checkPointDex == (numIntervals - 2) } {

									debug.if{ "\n\t- planning a new destination -\n".postln };

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
									debug.if{
										"\nNEXT checkpoints".postln;	nextCheckPoints.postln;
										"NEXT intervalDestinations".postln;	nextIntervalDestinations.postln; "".postln;
									};

									// schedule the next interval (2 ahead of current)
									grbl.goTo_(nextIntervalDestinations[scheduleDex][0], nextIntervalDestinations[scheduleDex][1], feed);
									lastScheduled = nextIntervalDestinations[scheduleDex];
									postf("\tScheduling % at %\n", nextIntervalDestinations[scheduleDex], feed);

								}

								// last interval checkpoint in the current destination
								{ checkPointDex > (numIntervals - 2) } {

									debug.if{ "\n\t- flipping to new direction, checkpoints, intervalDestinations -\n".postln };

									// update the checkpoint variables for the next checkpoint test
									directions = nextDirections;
									checkPoints = nextCheckPoints;
									intervalDestinations = nextIntervalDestinations;
									trackedDex = maxTravelAxisDex;

									// schedule the next interval(2 ahead of current),
									// on the way to the new destination
									grbl.goTo_(intervalDestinations[scheduleDex][0], intervalDestinations[scheduleDex][1], feed);
									lastScheduled = intervalDestinations[scheduleDex];
									postf("\tScheduling % at %\n", intervalDestinations[scheduleDex], feed);

								};

								checkPointDex	= (checkPointDex + 1) % numIntervals;
								scheduleDex	= (scheduleDex + 1) % numIntervals;
							}
						);
						grbl.stateUpdateRate.reciprocal.wait;
					}
				});

			}
		);

	}

	// this changes GRBL state request messages as well as the
	// frequency with which the periodic driving scheme checks
	// its position to plan future moves
	stateRefreshRate_ { |newRate|
		newRate !? { grbl.stateUpdateRate_(newRate) }
	}

	// the number of intervals into which each scheduled
	// destination is divided to form checkpoints by which to
	// schedule future moves
	numIntervals_ { |num|
		(num >= 3).if(
			{ numIntervals = num},
			{ warn( "Must use at least 3 intervals for move planning. numIntervals not updated." ) }
		)
	}

	stop {
		driveRoutine.stop;
		this.stopLfo;
		initialized = false;	// so re-initializes on next state update
		started = false;		// so sets origin as current work position
		lastScheduled = nil;	// remove lastScheduled to default to wPos on next drive
		driving = false;
		this.changed(\driving, driving);
	}

	free {
		fork({
			this.stop;
			0.2.wait;
			[lfoControlX, lfoControlY, plotterX, plotterY].do{|me| me !? {me.free} };
			if(gui.win.isClosed.not and: gui.freed.not){gui.free};
		}, AppClock);
	}
}

// TODO: add wait time after timeout so as not to accumulate lots of extra moves
