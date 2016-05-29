MotorDriverLfoView {

	// copyArgs
	var driver, grbl, <fr, historySecs;
	var elPast, azPast;
	var <cx, <cy;
	var scrn, scrnW, scrnH, <win, <userView, <ctlView;
	var <specs, <controls, <validLFOs;
	var leftWidth, nbw, baseColor, titleHeight = 25, txtH = 20, bxH = 20, internalMargins = 3, internalSpacing = 2;
	var <plotLayout, plotEmbedded = false, <freed=false;

	*new { |aMotorDriver, aGrbl, fr = 8, historySecs = 3|
		^ super.newCopyArgs(aMotorDriver, aGrbl, fr, historySecs).init;
	}

	init {
		leftWidth = 60;
		nbw = 35;

		cx = driver.lfoControlX;
		cy = driver.lfoControlY;

		driver.addDependant(this);
		cx.addDependant(this);
		cy.addDependant(this);
		grbl.addDependant(this);

		grbl.updateState_(true);

		baseColor = Color.hsv(0.08952380952381, 0.94086021505376, 0.72941176470588, 1);

		validLFOs = [
			SinOsc, LFPar, LFTri, LFCub,
			LFDNoise1, LFDNoise3
		].collect(_.asSymbol);

		specs = IdentityDictionary(know:true).putPairs([

			\center, IdentityDictionary(know:true).putPairs([
				\pan,	ControlSpec(grbl.xLimitHigh-1, grbl.xLimitLow+1,  default: 0),
				\tilt,	ControlSpec(grbl.yLimitHigh-1, grbl.yLimitLow+1,  default: 50),
			]),

			\range, IdentityDictionary(know:true).putPairs([
				\pan,	ControlSpec(0, grbl.xLimitHigh.abs*2, 5, 1, default: 15),
				\tilt,	ControlSpec(0, grbl.yLimitHigh.abs*2, 5, 1, default: 5),
			]),
		]);


		controls = IdentityDictionary(know: true).putPairs([

			\lfo,
			IdentityDictionary(know: true).putPairs([
				\pan,	IdentityDictionary(know: true).putPairs([
					\checkBox, CheckBox()
					.action_({ |bx|
						bx.value.asBoolean.if{
							controls.lfo.pan.menu.valueAction_(
								controls.lfo.pan.menu.value
							)
						}
					})
					.value_(cx.mixSynth.ctlSrcDex.asBoolean),

					\menu, PopUpMenu().items_( validLFOs )
					.action_({ |menu|
						cx.lfo_(
							menu.item.asSymbol,
							cx.freq, cx.low, cx.high
						)
					}).value_(
						validLFOs.indexOf(
							cx.curLfoUgens[cx.mixSynth.lfoDex]
						)
					),

					\numBox, NumberBox()
					.action_({ |bx|
						cx.freq_(bx.value.reciprocal);
					}).value_( cx.freq.reciprocal ),
				]),

				\tilt,	IdentityDictionary(know: true).putPairs([
					\checkBox, CheckBox()
					.action_({ |bx|
						bx.value.asBoolean.if{
							controls.lfo.tilt.menu.valueAction_(
								controls.lfo.tilt.menu.value
							)
						}
					})
					.value_(cy.mixSynth.ctlSrcDex.asBoolean),

					\menu, PopUpMenu().items_( validLFOs )
					.action_({ |menu|
						cy.lfo_(
							menu.item.asSymbol,
							cy.freq, cy.low, cy.high
						)
					}).value_(
						validLFOs.indexOf(
							cy.curLfoUgens[cy.mixSynth.lfoDex]
						)
					),

					\numBox, NumberBox()
					.action_({ |bx|
						cy.freq_(bx.value.reciprocal);
					}).value_( cy.freq.reciprocal ),

				]),

				\fadeTime, IdentityDictionary(know: true).putPairs([
					\numBox, NumberBox()
					.action_({ |bx|
						[cx, cy].do{ |ctl|
							ctl.fadeTime_(bx.value)
						}
					})
					.valueAction_( 14 ) // init fade time
				]),

				\active, IdentityDictionary(know: true).putPairs([
					\button, Button()
					.states_([["Inactive", Color.black, Color.new255(173, 72, 86)],["Active", Color.black, Color.new255(141, 205, 58)]])
					.action_({ |but|
						if( but.value.asBoolean,
							{	fork({
								var staticPos;

								// store current static position if source dex is 0 (static)
								staticPos = [cx, cy].collect{|ctl, i|
									if (ctl.mixSynth.ctlSrcDex == 0)
									{ctl.mixSynth.staticVal}
									{nil};
								};

								// set the lfo to the current world position immediately
								// (so it can then fade to the new value below)
								cx.value_( grbl.wPos[0], 0 );
								cy.value_( grbl.wPos[1], 0 );
								0.3.wait;

								cx.source_('static', 0);
								cy.source_('static', 0);
								0.3.wait;

								// tell the motors to go to the control signals
								// (which are at it's world position)
								driver.driveLfo;

								// now fade the control source to the stored new static/lfo target
								[cx, cy].do{ |ctl, i|
									staticPos[i].notNil.if(
										{ ctl.value_( staticPos[i],
											controls.lfo.fadeTime.numBox.value );
										},
										{ ctl.source_('lfo',
											controls.lfo.fadeTime.numBox.value );
										}
									);
								};

							}, AppClock);
							},{
								driver.stopLfo;
							}
						);
					})
					.value_(0)
				]),
			]),

			\fixedPos,	IdentityDictionary(know: true).putPairs([
				\pan,	IdentityDictionary(know: true).putPairs([
					\checkBox, CheckBox()
					.action_({ |bx|
						bx.value.asBoolean.if{
							controls.fixedPos.pan.numBox.valueAction_(
								controls.fixedPos.pan.numBox.value
							)
						}
					})
					.value_((cx.mixSynth.ctlSrcDex - 1).abs.asBoolean),

					\numBox, NumberBox()
					.action_({ |bx|
						cx.value_(bx.value);
					}).value_( cx.mixSynth.staticVal ),
				]),

				\tilt,	IdentityDictionary(know: true).putPairs([
					\checkBox, CheckBox()
					.action_({ |bx|
						bx.value.asBoolean.if{
							controls.fixedPos.tilt.numBox.valueAction_(
								controls.fixedPos.tilt.numBox.value
							)
						}
					})
					.value_((cy.mixSynth.ctlSrcDex - 1).abs.asBoolean),

					\numBox, NumberBox()
					.action_({ |bx|
						cy.value_(bx.value);
					}).value_( cy.mixSynth.staticVal ),

				]),
			]),

			\center,	IdentityDictionary(know: true).putPairs([

				\pan,	IdentityDictionary(know: true).putPairs([

					\numBox, NumberBox()
					.action_({ |bx|
						// first update the driver, which includes bound checking
						driver.centerX_( bx.value );
						// then retrieve bounds-checked values
						cx.lfoBounds_( *driver.getBounds( 0 ) );
					})
					.value_(driver.centerX),

					\slider, Slider()
					.action_({ |sl|
						driver.centerX_( specs.center.pan.map(sl.value) );
						cx.lfoBounds_( *driver.getBounds( 0 ) );
					})
					.value_(specs.center.pan.unmap(driver.centerX)),
				]),

				\tilt,	IdentityDictionary(know: true).putPairs([

					\numBox, NumberBox()
					.action_({ |bx|
						driver.centerY_( bx.value );
						cy.lfoBounds_( *driver.getBounds( 1 ) );
					})
					.value_(driver.centerY),

					\slider, Slider()
					.action_({ |sl|
						driver.centerY_( specs.center.tilt.map(sl.value) );
						cy.lfoBounds_( *driver.getBounds( 1 ) );
					})
					.value_(specs.center.tilt.unmap(driver.centerY)),
				]),
			]),

			\range,
			IdentityDictionary(know: true).putPairs([

				\pan,
				IdentityDictionary(know: true).putPairs([

					\numBox, NumberBox()
					.action_({ |bx|
						driver.rangeX_(bx.value);
						cx.lfoBounds_( *driver.getBounds( 0 ) );

					}).value_(driver.rangeX),

					\slider, Slider()
					.action_({ |sl|
						driver.rangeX_(specs.range.pan.map(sl.value));
						cx.lfoBounds_( *driver.getBounds( 0 ) );
					}).value_(specs.range.pan.unmap(driver.rangeX)),
				]),

				\tilt,
				IdentityDictionary(know: true).putPairs([

					\numBox, NumberBox()
					.action_({ |bx|
						driver.rangeY_(bx.value);
						cy.lfoBounds_( *driver.getBounds( 1 ) );
					}).value_(driver.rangeY),

					\slider, Slider()
					.action_({ |sl|
						driver.rangeY_(specs.range.tilt.map(sl.value));
						cy.lfoBounds_( *driver.getBounds( 1 ) );
					}).value_(specs.range.tilt.unmap(driver.rangeY)),
				]),
			]),


			\goTo,
			IdentityDictionary(know: true).putPairs([

				\pan,	NumberBox().value_(0),
				\tilt,	NumberBox().value_(0),
				\feedTime,	NumberBox().value_(6),

				\go,	Button()
				.states_([["Go", Color.new255(*45!3), Color.new255(64, 134, 183)]])
				.action_({ |but|
					var toX, toY, wPos, distX, distY, maxDist, feedRate;

					// driver.driving.if{ driver.stop };
					driver.driving.if{ driver.stopLfo };

					toX = controls.goTo.pan.value;
					toY = controls.goTo.tilt.value;
					wPos = grbl.wPos;
					distX = (toX - wPos[0]).abs;
					distY = (toY - wPos[1]).abs;
					maxDist = max(distX, distY);
					feedRate = maxDist / controls.goTo.feedTime.value;

					grbl.goTo_( toX, toY,
						driver.feedRateEnv.at(feedRate.clip(1, 40))
					)
				})
			]),

			// \end,
			// Button().states_([["End", Color.gray],["End", Color.red]])
			// .action_({ |but|
			// 	(but.value == 1).if{driver.stop};
			// }),
			// \run,
			// Button().states_([["Run", Color.gray],["Run", Color.blue]])
			// .action_({ |but|
			// 	(but.value == 1).if{driver.drive};
			// }),

			\motorState,
			StaticText().string_("Unknown").stringColor_(Color.gray)
			.align_('center').background_(Color.gray.alpha(0.25)),

			\cyclePeriod,
			StaticText().string_("Cycle Period: ").background_(Color.yellow.alpha_(0.3)),

			\multPeriod,
			StaticText().string_(
				format("% secs", (driver.basePeriod / driver.numBasePeriodDivs).round(0.1))
			).align_(\left),

			\maxRate,
			StaticText().string_(format("Current max possible rate:\n% of %", nil, driver.motorMaxRate.round(0.1)))
			.background_(Color.yellow.alpha_(0.3)),

			\status,
			StaticText().string_("Status."),

			\plotChk,
			CheckBox().action_({ |chk|
				chk.value.asBoolean.if(
					{ [driver.plotterX, driver.plotterY].do(_.start) },
					{ [driver.plotterX, driver.plotterY].do(_.stop) }
				)
			}).maxWidth_(20).value_(1),

			\unlock,
			Button().states_([["Unlock", Color.gray]])
			.action_({
				grbl.unlock;
			}),

			\halt,
			Button().states_([["Halt", Color.gray]])
			.action_({
				driver.stop;
				grbl.pause;
			}),

			\resume,
			Button().states_([["Resume", Color.gray]])
			.action_({
				grbl.resume;
			}),

			\resetGRBL,
			Button().states_([["Reset", Color.gray]])
			.action_({
				driver.stop;
				grbl.reset;
			}),

			\home,
			Button().states_([["Home Routine", Color.gray]])
			.action_({
				driver.stop;
				grbl.home;
			}),
		]);

		this.makeMotorDisplay;
		this.makeWindow;
		win.refresh;
	}

	getCtlLayoutSlider { |spec, numBox, slider, color, string|

		^View().background_(color ?? Color.gray.alpha_(0.1)).layout_(
			VLayout(
				HLayout(
					[StaticText().string_(spec.minval).align_('left').maxHeight_(txtH)
					.stringColor_( Color.black.alpha_(0.5) )
					.fixedWidth_(nbw), a: \bottomLeft],
					35, numBox.fixedWidth_(nbw).maxHeight_(bxH).align_('center'),
					string.notNil.if(
						{
							string.isKindOf(QStaticText).if(
								{string},
								{StaticText().string_(string).align_(\left).fixedWidth_(nbw).maxHeight_(txtH)}
							)
						},
						{35}),
					[StaticText().string_(spec.maxval).align_('right').maxHeight_(txtH)
					.stringColor_( Color.black.alpha_(0.5) )
					.fixedWidth_(nbw), a: \bottomRight],
				),
				slider.orientation_('horizontal')
			).margins_(internalMargins).spacing_(internalSpacing)
		)
	}

	getCtlLayoutMenu { | checkBox, menu, numBox, string, color |

		^View().background_(color ?? Color.gray.alpha_(0.1)).layout_(
			HLayout(
				[ checkBox, a: 'left'],
				[ menu.maxHeight_(bxH), a: 'left'],
				string.notNil.if(
					{
						string.isKindOf(QStaticText).if(
							{string},
							{StaticText().string_(string).align_('left').maxHeight_(txtH)}
						)
					},{ nil }
				),
								[ numBox.fixedWidth_(nbw).maxHeight_(bxH).align_('center'), a: 'left'],
				nil
			)
			.margins_(internalMargins).spacing_(internalSpacing),
		)
	}

	getCtlLayoutNumBox { | label, preString, checkBox, numBox, postString, color |
		^View().background_(color ?? Color.gray.alpha_(0.1)).layout_(
			VLayout(
				label !? {StaticText().string_(label).align_('center').maxHeight_(txtH)},
				HLayout(
					[checkBox, a: 'left'],
					5,
					preString.notNil.if({
						preString.isKindOf(QStaticText).if(
							{preString},
							{StaticText().string_(preString).align_('right').maxHeight_(txtH)}
						)
					},{ nil }),
					[numBox.fixedWidth_(nbw).maxHeight_(bxH).align_('center'), a: 'left'],
					postString.notNil.if({
						postString.isKindOf(QStaticText).if(
							{postString},
							{StaticText().string_(postString).align_('left').maxHeight_(txtH)}
						)
					},{ nil }),
					nil
				).margins_(internalMargins).spacing_(internalSpacing)
			)
		)
	}

	getColor { |huescl = 0.05, offset = 1 |
		^Color.hsv(*baseColor.asHSV
			.put(0, (baseColor.asHSV[0] + huescl).clip(0,1))
			.put(1, baseColor.asHSV[1] * offset )
		)
	}

	makeWindow {

		scrn = Window.screenBounds;
		scrnW = scrn.width;
		scrnH = scrn.height;
		win = Window("Motor Driver", Rect(2*scrnW/3, 0, scrnW/3, 2*scrnH/3)).front;
		win.onClose_({this.free});

		ctlView = View().layout_(
			VLayout(
				View()
				.layout_(
					VLayout(
						// Go To controls
						// HLayout(
						// 	nil,
						// ),
						// 10,
						// Control Signal label and button
						HLayout(
							nil,
							StaticText().string_("Control Signal").font_(Font.default.size_(16)).fixedHeight_(titleHeight).align_(\center),
							10,
							[ controls.lfo.active.button, a: 'center'],
							nil,
							// [ controls.plotChk, a: 'right'],
							// [ StaticText().string_("Plot").align_('left').maxWidth_(25), a: 'right' ],
						),

						plotLayout = HLayout(
							// View().fixedHeight_(175).fixedWidth_(leftWidth),
							View().fixedHeight_(175).fixedWidth_(leftWidth).layout_(
								VLayout(
									HLayout(
										[ StaticText().string_("Plot").align_('right').maxWidth_(25), a: 'left' ],
										[ controls.plotChk, a: 'left'],
									).margins_(5),
									nil
									// Control Sig controls
									// 5,
									// [ StaticText().string_("Fade Time").align_('center'), a: 'top' ],
									// [ controls.lfo.fadeTime.numBox.maxWidth_(35).align_('center'), a: 'top'],
									// nil,
									// [ controls.plotChk, a: 'bottom'],
									// [ StaticText().string_("Plot").align_('center').maxWidth_(25), a: 'bottom' ],
									// 5
								).margins_(0).spacing_(0)
							)
						).margins_(0).spacing_(0),

						HLayout(
							leftWidth,
							[StaticText().string_("Pan").align_(\center), a: \center],
							[StaticText().string_("Tilt").align_(\center), a: \center],
						),

						HLayout(
							StaticText().string_("Fixed").fixedWidth_(leftWidth)
							.stringColor_(Color.black.alpha_(0.8))
							.align_(\center).background_(this.getColor(0.04, 0.4)),
							this.getCtlLayoutNumBox(
								nil, //"Pan",
								"Go to ",
								controls.fixedPos.pan.checkBox,
								controls.fixedPos.pan.numBox,
								"deg",
								this.getColor(0.05),
							),
							this.getCtlLayoutNumBox(
								nil, //"Tilt",
								"Go to ",
								controls.fixedPos.tilt.checkBox,
								controls.fixedPos.tilt.numBox,
								"deg",
								this.getColor(0.05),
							),
						),

						HLayout(
							StaticText().string_("LFO").fixedWidth_(leftWidth)
							.stringColor_(Color.black.alpha_(0.8))
							.align_(\center).background_(this.getColor(0.08, 0.4)),
							this.getCtlLayoutMenu(
								controls.lfo.pan.checkBox,
								controls.lfo.pan.menu.maxHeight_(bxH),
								controls.lfo.pan.numBox.maxHeight_(bxH),
								"Period",
								this.getColor(0.08),
							),
							this.getCtlLayoutMenu(
								controls.lfo.tilt.checkBox,
								controls.lfo.tilt.menu,
								controls.lfo.tilt.numBox,
								"Period",
								this.getColor(0.08),
							),
						),

						HLayout(
							StaticText().string_("Center").fixedWidth_(leftWidth)
							.stringColor_(Color.black.alpha_(0.8))
							.align_(\center).background_(this.getColor(0.08, 0.4)),
							this.getCtlLayoutSlider( specs.center.pan,
								controls.center.pan.numBox,
								controls.center.pan.slider,
								this.getColor(0.08), "deg"
							),
							this.getCtlLayoutSlider( specs.center.tilt,
								controls.center.tilt.numBox,
								controls.center.tilt.slider,
								this.getColor(0.08), "deg"
							),
						),

						HLayout(
							StaticText().string_("Range").fixedWidth_(leftWidth)
							.stringColor_(Color.black.alpha_(0.8))
							.align_(\center).background_(this.getColor(0.08, 0.4)),
							this.getCtlLayoutSlider( specs.range.pan,
								controls.range.pan.numBox,
								controls.range.pan.slider,
								this.getColor(0.08), "deg"
							),
							this.getCtlLayoutSlider( specs.range.tilt,
								controls.range.tilt.numBox,
								controls.range.tilt.slider,
								this.getColor(0.08), "deg"
							)
						),

						// Status, stop, run, reset, home
						HLayout(
							controls.motorState.fixedWidth_(leftWidth),
							// controls.end, controls.run
							controls.status.minWidth_(300).maxHeight_(25),
							nil,
							[ StaticText().string_("Fade Time").align_('right'), a: 'right' ],
							[ controls.lfo.fadeTime.numBox.maxWidth_(35).align_('center'), a: 'right'],
						),

						HLayout(controls[\halt], controls.resume, 30, controls.unlock, controls.resetGRBL, controls.home)
					).margins_(5).spacing_(5)
				).background_(Color.gray.alpha_(0.1))
			).margins_(0)
		);

		userView.layout_( HLayout(
			[ View().layout_( VLayout(
				VLayout(
					[ StaticText().string_("Travel").align_('left')
						.maxHeight_(txtH).fixedWidth_(leftWidth).stringColor_(Color.new255(*(126!3))),
						a: 'left'],
					2,
					HLayout(
						[controls.goTo.pan
							// .stringColor_(Color.new255(*(106!3))).background_(Color.new255(*(55!3)))
							.fixedSize_(Size(nbw, bxH)).align_('center')
							, a: \left],
						StaticText().string_("Pan").align_('left')
						.fixedHeight_(txtH).fixedWidth_(nbw).stringColor_(Color.new255(*(106!3))),
					).margins_(0).spacing_(4),
					HLayout(
						[controls.goTo.tilt
							.fixedSize_(Size(nbw, bxH)).align_('center')
							// .background_(Color.new255(*(55!3))).stringColor_(Color.new255(*(106!3)))
							, a: \left],
						StaticText().string_("Tilt").align_('left')
						.fixedHeight_(txtH).fixedWidth_(nbw).stringColor_(Color.new255(*(106!3))),
					).margins_(0).spacing_(4),
					HLayout(
						[controls.goTo.feedTime
							.fixedSize_(Size(nbw, bxH)).align_('center')
							// .background_(Color.new255(*(55!3))).stringColor_(Color.new255(*(106!3)))
							, a: \left],
						StaticText().string_("Dur").align_('left')
						.fixedHeight_(txtH).fixedWidth_(nbw).stringColor_(Color.new255(*(106!3))),
					).margins_(0).spacing_(4),
					4,
					[controls.goTo.go.maxWidth_(nbw), a: 'left'],
				).margins_(0).spacing_(2),
				nil,
			).margins_(4),
			// ).background_(Color.new255(*(125!3))),
			// //.background_(this.getColor(0.02)),
			), a: \topLeft ],
			nil
		).margins_(0)
		);

		win.layout_(
			VLayout(
				userView,
				ctlView
			).margins_(0).spacing_(0)
		);

		win.refresh;

		controls.goTo.pan.stringColor_(Color.new255(*(106!3))).background_(Color.new255(*(55!3)));
		controls.goTo.tilt.stringColor_(Color.new255(*(106!3))).background_(Color.new255(*(55!3)));
		controls.goTo.feedTime.stringColor_(Color.new255(*(106!3))).background_(Color.new255(*(55!3)));

		this.embedMonitor
	}

	embedMonitor {
		plotEmbedded.not.if{
			{
				driver.plotDriver;
				0.5.wait;
				plotLayout.insert(driver.plotterX.mon.plotter.parent.view, 1);
				plotLayout.insert(driver.plotterY.mon.plotter.parent.view, 2);
				plotEmbedded = true;
			}.fork(AppClock)
		}
	}

	frameRate {^fr}

	frameRate_ { |hz|
		fr = hz;
		elPast = Array.fill(fr * historySecs, {0});
		azPast = Array.fill(fr * historySecs, {0});
		userView.frameRate_(fr);
	}

	makeMotorDisplay {
		var traceColor, arrayColors;
		// elPast, azPast,

		traceColor = Color.red;
		arrayColors = [Color.fromHexString("4F71A2"),Color.fromHexString("9CC4FF"), Color.fromHexString("13243E")];

		elPast = Array.fill(fr * historySecs, {0});
		azPast = Array.fill(fr * historySecs, {0});


		userView = UserView()
		.background_(Color.new255(*45!3))
		.animate_(true).frameRate_(fr)
		.minWidth_(300).minHeight_(300)
		.resize_(5);

		userView.drawFunc_{ |view|
			var azNegRad, elRad, elRadAbs, minDim, r, d, cen, arcH;
			var arrHeight, wPos, rangeBoundsRad;
			var circleViewRatio, maxMinStr, dirPnt, azLineClr;
			var azPnt, drawPnt, omniRad, omniDiam, diam, gainColor, gainPnt, ovalRect;

			wPos = grbl.wPos;
			azNegRad = wPos[0].neg.degrad; // optimize for drawing coords
			elRad = wPos[1].degrad;
			elRadAbs = elRad.abs;

			azPast = azPast.rotate(1);
			azPast[0] = azNegRad;

			elPast = elPast.rotate(1);
			elPast[0] = elRad;

			minDim = [userView.bounds.width, userView.bounds.height].minItem;

			r = minDim/2 * 0.025;
			d = r*2;
			circleViewRatio = 0.8;
			arcH = minDim * circleViewRatio / 2;	// height of the "fan" arc
			diam = 2 * arcH;

			cen = view.bounds.center; // center drawing origin

			Pen.translate(cen.x, cen.y);

			Pen.push;
			Pen.rotate(azNegRad);

			// draw speaker with outline
			// arrHeight = diam * ((0.5pi-elRadAbs) / (0.5pi));
			arrHeight = diam * cos(elRadAbs);

			ovalRect = Rect(
				0 - arcH, 0 - (arrHeight * 0.5),
				diam, arrHeight
			);
			Pen.addOval( ovalRect );

			Pen.fillAxialGradient(
				// ovalRect.bounds.leftTop,
				// ovalRect.bounds.leftBottom,
				elRad.isPositive.if(
					{ovalRect.bounds.leftBottom},
					{ovalRect.bounds.leftTop}
				),
				elRad.isPositive.if(
					{ovalRect.bounds.leftTop},
					{ovalRect.bounds.leftBottom}
				),
				// arrayColors[1].blend(arrayColors[elRad.isPositive.if{2}{1}], elRadAbs/0.5pi ),
				// arrayColors[1].blend(arrayColors[elRad.isPositive.if{1}{2}], elRadAbs/0.5pi),
				Color.hsv(0.5993265993266, 0.38823529411765, (elRadAbs/0.5pi).sqrt * 0.5 + 0.5, 1),
				Color.hsv(0.5993265993266, 0.38823529411765, 1 - (elRadAbs/0.5pi).sqrt * 0.5, 1)
			);

			Pen.strokeColor_(arrayColors[2]);
			Pen.strokeOval( ovalRect );

			// trajectory direction point
			dirPnt = Polar( sin( elRad ).neg, 0 ).asPoint
			.rotate(0.5pi)	// convert ambi to screen coords
			* arcH;			// scale normalized points to arcH

			// draw azimuth point w/o perspective
			azPnt = Polar(-1, 0).asPoint.rotate(0.5pi) * arcH;
			Pen.fillColor_(Color.yellow);
			Pen.fillOval( Rect(azPnt.x-r, azPnt.y-r, d, d) );
			QPen.stringCenteredIn(
				wPos[0].round(0.1).asString,
				Rect(azPnt.x-(r*10), azPnt.y-(r*15), d*10, d*10)
			);

			// line to trajectory point
			Pen.strokeColor_(Color.fromHexString("87B7FD"));
			Pen.line(dirPnt, 0@0).stroke;
			Pen.pop;

			// draw range
			Pen.push;

			rangeBoundsRad = driver.getBounds.degrad;
			Pen.rotate( driver.centerX.neg.degrad - (pi/2));

			// arg center, innerRadius, outerRadius, startAngle, sweepLength;
			Pen.addAnnularWedge( 0@0,
				sin(rangeBoundsRad[1][0]) * arcH,
				sin(rangeBoundsRad[1][1]) * arcH,
				driver.rangeX.half.degrad, driver.rangeX.neg.degrad
			);
			Pen.fillColor_( Color.yellow.alpha_(0.3) ).fill;
			Pen.pop;

			// Pen.addAnnularWedge( 0@0, 5, arcH, 0, 2pi );
			// Pen.fillColor_(Color.gray(0.9)).fill;

			// background circles
			Pen.strokeColor_(Color.gray.alpha_(0.3));
			3.do{|i|
				var val = cos(pi/6 * (i)); // = cos(pi/2 * (i+1) / 3);
				var xyOffset = (arcH * val).neg;
				var thisrad = diam*val;
				Pen.strokeOval( Rect( xyOffset, xyOffset, thisrad, thisrad ));
			};

			// draw history trace
			azPast.size.do{ |i|
				var color, pnt;
				Pen.push;
				// color = traceColor.alpha_( (1 - ((i+1)/(azPast.size))) * 0.5);
				color = traceColor.alpha_( (1 - ((i+1)/(azPast.size))) * 0.5);
				Pen.fillColor_( color );
				Pen.rotate(azPast[i]);
				Pen.fillOval( Rect(r.neg, sin(elPast[i]).neg * arcH-r, d, d) );
				Pen.pop;
			};

			Pen.push;
			Pen.rotate(azNegRad);
			// draw elevations with perspective
			Pen.fillColor_(traceColor);
			Pen.fillOval( Rect(dirPnt.x-r, dirPnt.y-r, d, d) );

			Pen.pop;
		}
	}

	free {
		win !? {win.close};
		driver.removeDependant(this);
		cx.removeDependant(this);
		cy.removeDependant(this);
		grbl.removeDependant(this);
		freed = true;
		driver.free;
	}

	update {
		| who, what ... args |
		if( who == driver, {
			switch ( what,

				\centerX, {
					controls.center.pan.numBox.value = args[0];
					controls.center.pan.slider.value = specs.center.pan.unmap(args[0]);
				},
				\centerY, {
					controls.center.tilt.numBox.value = args[0];
					controls.center.tilt.slider.value = specs.center.tilt.unmap(args[0]);
				},

				\rangeX, {
					controls.range.pan.numBox.value = args[0];
					controls.range.pan.slider.value = specs.range.pan.unmap(args[0]);
				},

				\rangeY, {
					controls.range.tilt.numBox.value = args[0];
					controls.range.tilt.slider.value = specs.range.tilt.unmap(args[0]);
				},

				\basePeriod, {
					controls.basePeriod.numBox.value_(args[0]);
					controls.basePeriod.slider.value_(
						specs.basePeriod.unmap(args[0])
					);
					controls.multPeriod.string_(
						format("% secs", (args[0] / driver.numBasePeriodDivs).round(0.1) )
					);
				},
				\numBasePeriodDivs, {
					controls.numBasePeriodDivs.numBox.value_(args[0]);
					controls.numBasePeriodDivs.slider.value_(
						specs.numBasePeriodDivs.unmap(args[0])
					);
				},
				\baseAxis, {
					controls.basePeriod.checkBox.pan.value = (args[0]==0);
					controls.basePeriod.checkBox.tilt.value = (args[0]==1);
				},
				\driveMode, {
					switch( args[0],
						'random',	{
							controls.driveMode.random.value_(1); controls.driveMode.periodic.value_(0); },
						'periodic',	{
							controls.driveMode.random.value_(0); controls.driveMode.periodic.value_(1); },
					);
				},

				\status, {
					controls.status.string_(Date.getDate.format("%I:%M  ") ++ args[0])
				},

				\driving, {
					// if(args[0],
					// 	{ controls.run.value_(1); controls.end.value_(0); },
					// 	{ controls.run.value_(0); controls.end.value_(1); }
					// );
					controls.lfo.active.button.value_( if(args[0]){1}{0} );
				},
				\cyclePeriod, {
					controls.cyclePeriod.string_( format("Cycle Period: %", args[0].round(0.01)) )
				},
				\randPeriod, {
					controls.randPeriod.numBox.value_(args[0]);
					controls.randPeriod.slider.value_(
						specs.randPeriod.unmap(args[0])
					);
				},
				\maxRate, {
					controls.maxRate.string_( format(
						"Current max possible rate:\n% of %", args[0].round(0.1),
						driver.motorMaxRate.round(0.1)
					))
				},

				\lfoStaticVal, {
					var which, val;
					#which, val = args;
					controls.fixedPos[which].numBox.value_(val);
				},
				\lfo, {
					var which, ugen, freq, lfo;
					#which, ugen, freq = args;
					which = [\pan, \tilt].at(which);
					lfo = controls.lfo[which];
					lfo.menu.value_(lfo.menu.items.indexOf(ugen));
					lfo.numBox.value_(freq.reciprocal);
				},
			)
		});

		if( who == grbl, {
			switch ( what,

				\mode, {
					var color;
					color = switch(args[0],
						"Run", {
							// clear the status text
							defer{ controls.status.string_("") };
							Color.green;
						},
						"Idle", { Color.gray },
						"Alarm", {
							defer{ controls.status.string_("Alarm mode. Unlock (if safe) or run homing routine.") };
							Color.red;
						},
						"Queue", {Color.yellow},
						"Home", {Color.yellow},
					);
					defer{
						// try in case window is already closed
						try {
							controls.motorState.string_(args[0]).stringColor_(color ?? Color.gray);
						}
					};
				},

				\error, {
					defer{ controls.status.string_(args[0]) }
				},

				\status, {
					defer{ controls.status.string_(args[0]) }
				}
			);
		});

		if( who == cx, {
			switch ( what,
				\ctlSrcDex, { defer {
					controls.lfo.pan.checkBox.value_(args[0].asBoolean);
					controls.fixedPos.pan.checkBox.value_(args[0].asBoolean.not);
				}},

				\staticVal, { defer {
					controls.lfo.pan.checkBox.value_(false);
					controls.fixedPos.pan.checkBox.value_(true);
					controls.fixedPos.pan.numBox.value_(args[0]);
				}}
			);
		});

		if( who == cy, {
			switch ( what,
				\ctlSrcDex, { defer {
					controls.lfo.tilt.checkBox.value_(args[0].asBoolean);
					controls.fixedPos.tilt.checkBox.value_(args[0].asBoolean.not);
				}},

				\staticVal, { defer {
					controls.lfo.tilt.checkBox.value_(false);
					controls.fixedPos.tilt.checkBox.value_(true);
					controls.fixedPos.tilt.numBox.value_(args[0]);
				}}
			);
		});
	}
}