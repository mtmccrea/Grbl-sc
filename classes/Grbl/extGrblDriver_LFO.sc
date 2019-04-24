/*
   Michael McCrea, 2016-2019
   Support for driving Grbl with LFOs
*/

+ GrblDriver {

	initLfoDriving { |xlo = 0, xhi = 1, ylo = 0, yhi = 1, finishedCond|
		var cond = Condition();
		lfoLowX = xlo;
		lfoHighX = xhi;
		lfoLowY = ylo;
		lfoHighY = yhi;
		rangeX	= xhi - xlo;
		rangeY	= yhi - ylo;
		centerX = xlo + rangeX.half;
		centerY = ylo + rangeY.half;
		driving	= false;

		fork {
			if (lfoDrivingEnabled.not) {
				// write grbl position to a bus
				this.writePosToBus_(true, completeCondition: cond);
				cond.wait; cond.test_(false);

				// start x-axis LFO
				lfoControlX = ControlFade( 8, onComplete: {cond.test_(true).signal});
				cond.wait; cond.test_(false);
				lfoControlX.lfoSynths.do{|synth| synth.freq_(20.reciprocal)};
				lfoControlX.lfoBounds_(lfoLowX, lfoHighX, 0);

				// start y-axis LFO
				lfoControlY = ControlFade( 8, onComplete: {cond.test_(true).signal});
				cond.wait;
				lfoControlY.lfoSynths.do{|synth| synth.freq_(20.reciprocal)};
				lfoControlY.lfoBounds_(lfoLowY, lfoHighY, 0);

				// initialize driving controls to wPos
				lfoControlX.value_( wPos[0], 0 );
				lfoControlY.value_( wPos[1], 0 );

				lfoDrivingEnabled = true;
				finishedCond !? { finishedCond.test_(true).signal };
			}
		}
	}


	driveLfo {
		if (lfoDrivingEnabled) {
			this.followSerialBufXY_(
				lfoControlX.controlBus, lfoControlY.controlBus,
				motorInstructionRate ?? 40,
				stateUpdateRate ?? 8
			);
			driving = true;
			this.changed(\driving, driving);
		}{
			"lfo driving not enabled".warn

		}
	}

	getBounds { |whichAxis|
		if (whichAxis.notNil) {
			^[[lfoLowX, lfoHighX],[lfoLowY, lfoHighY]].at(whichAxis)
		}{
			^[[lfoLowX, lfoHighX],[lfoLowY, lfoHighY]]
		}
	}

	/* Movement bounds methods */

	bounds_ { |xlo, xhi, ylo, yhi|
		var lo, hi, center, range;

		if (xlo.notNil or: xhi.notNil) {
			hi = xhi ?? lfoHighX;
			lo = xlo ?? lfoLowX;
			center = (lo + hi).half;
			range = hi - lo;
			this.centerX_(center, range);
		};

		if (ylo.notNil or: yhi.notNil) {
			hi = yhi ?? lfoHighY;
			lo = ylo ?? lfoLowY;
			center = (lo + hi).half;
			range = hi - lo;
			this.centerY_(center, range);
		};
	}

	centerX_ { |centerDegree, newRangeX|
		centerDegree ?? { "No center specified".error; ^this };
		centerX = centerDegree.clip(xLimitLow + 1, xLimitHigh - 1);
		this.rangeX_(newRangeX ?? rangeX); // re-calculate x range, set lfoLowX/lfoHighX
		this.changed(\centerX, centerX);
	}

	centerY_ { |centerDegree, newRangeY|
		centerDegree ?? { "No center specified".error; ^this };
		centerY = centerDegree.clip(yLimitLow + 1, yLimitHigh - 1);
		this.rangeY_(newRangeY ?? rangeY); // re-calculate y range, set lfoLowY/lfoHighY
		this.changed(\centerY, centerY);
	}

	// fixed at center
	rangeX_ { |rangeDegree|
		var lo, hi, clipped = false;
		rangeDegree ?? { "No range specified".error; ^this };
		lo = centerX - rangeDegree.half;
		hi = centerX + rangeDegree.half;
		clipped = [lo, hi].collect({ |me|
			me.inRange(xLimitLow+1, xLimitHigh-1).not
		}).includes(true);
		#lo, hi = [lo, hi].clip(xLimitLow+1, xLimitHigh-1); // +/-1 just in case

		// hold center position, don't allow range to grow if one bound hits a limit
		if (clipped) {
			var halfRange;
			halfRange = min((hi - centerX).abs, (centerX - lo).abs);
			lo = centerX - halfRange;
			hi = centerX + halfRange;
		};

		lfoLowX = lo;
		lfoHighX = hi;
		rangeX = lfoHighX - lfoLowX;
		this.changed(\rangeX, rangeX);

		if (clipped) {
			var msg = "Clipping requested X range";
			warn(msg);
			this.changed(\status, msg);
		};
	}

	// fixed at center
	rangeY_ { |rangeDegree|
		var lo, hi, clipped = false;
		rangeDegree ?? { "No range specified".error };

		lo = centerY - rangeDegree.half;
		hi = centerY + rangeDegree.half;
		clipped = [lo, hi].collect({ |me|
			me.inRange(yLimitLow+1, yLimitHigh-1).not
		}).includes(true);

		#lo, hi = [lo, hi].clip(yLimitLow + 1, yLimitHigh - 1); // +/-1 just in case

		// hold center position, don't allow range to grow if one bound hits a limit
		clipped.if{
			var halfRange;
			halfRange = min((hi - centerY).abs, (centerY - lo).abs);
			lo = centerY - halfRange;
			hi = centerY + halfRange;
		};

		lfoLowY = lo;
		lfoHighY = hi;
		rangeY = lfoHighY - lfoLowY;
		this.changed(\rangeY, rangeY);

		clipped.if{
			var msg;
			msg = "Clipping requested Y range";
			warn(msg);
			this.changed(\status, msg);
		};
	}

	bounds {
		^Rect(lfoLowX, lfoLowY,  rangeX, rangeY)
	}

	plotDriver {
		if (lfoDrivingEnabled) {
			plotterX = ControlPlotter(
				[lfoControlX.busnum, posBus.bus], 2,
				plotLength: 35, refresh_rate: 10, plotMode: 'linear',
			).start.bounds_(xLimitLow, xLimitHigh)
			.plotColors_([Color.gray, Color.red]);

			plotterY = ControlPlotter(
				[lfoControlY.busnum, posBus.bus+1], 2,
				plotLength: 35, refresh_rate: 10, plotMode: 'linear',
			).start.bounds_(yLimitLow, yLimitHigh)
			.plotColors_([Color.gray, Color.blue]);

		}{
			"lfo driving not enabled".warn;
		}
	}

	stopLfo {
		this.unfollow;
		driving = false;
		this.changed(\driving, driving);
	}

	cleanupLfo {
		fork(
			{
				this.stopLfo;
				0.2.wait;
				[lfoControlX, lfoControlY, plotterX, plotterY].do{ |me|
					me !? {me.free}
				};
			}, AppClock
		);
	}

	// free {
	// 	fork({
	// 		this.stopLfo;
	// 		0.2.wait;
	// 		[lfoControlX, lfoControlY, plotterX, plotterY].do{|me| me !? {me.free} };
	// 		if(gui.win.isClosed.not and: gui.freed.not){gui.free};
	// 	}, AppClock);
	// }

}

/*
	/* Preset support */

	// TODO: check the preset settings with Rover to make sure preset backups work OK

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
*/