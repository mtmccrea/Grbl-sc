// MotorDriverView Prototype
(
var scrn, scrnW, scrnH, win;
var specs, controls, getLayout, leftWidth;
var baseColor, getColor;

scrn = Window.screenBounds;
scrnW = scrn.width;
scrnH = scrn.height;
win = Window("Motor Driver", Rect(2*scrnW/3, 0, scrnW/3, scrnH)).front;
baseColor = Color.hsv(0.08952380952381, 0.94086021505376, 0.72941176470588, 1);

specs = IdentityDictionary(know:true).putPairs([

	\center, IdentityDictionary(know:true).putPairs([
		\pan,	ControlSpec(140, -140,  default: 0),
		\tilt,	ControlSpec(-70, 70,  default: 50),
	]),

	\range, IdentityDictionary(know:true).putPairs([
		\pan,	ControlSpec(0, 280, default: 15),
		\tilt,	ControlSpec(0, 140, default: 5),
	]),
	\basePeriod,	ControlSpec(2, 30, default: 12),
	\basePeriodDiv,	ControlSpec(1, 8, step: 1, default: 1),
]);


controls = IdentityDictionary(know: true).putPairs([

	\center,
	IdentityDictionary(know: true).putPairs([
		\pan,	IdentityDictionary(know: true).putPairs([

			\numBox, NumberBox()
			.action_({ |bx|
				// do something with:
				// specs.center.pan.map(bx.value);
			}).value_(specs.center.pan.default),

			\slider, Slider()
			.action_({ |sl| var val;
				val = specs.center.pan.map(sl.value);
				// do something with val
			}).value_(specs.center.pan.unmap(specs.center.pan.default)),
		]),

		\tilt,	IdentityDictionary(know: true).putPairs([

			\numBox, NumberBox()
			.action_({ |bx|
				// do something with:
				// specs.center.pan.map(bx.value);
			}).value_(specs.center.tilt.default),

			\slider, Slider()
			.action_({ |sl| var val;
				val = specs.center.tilt.map(sl.value);
				// do something with val
			}).value_(specs.center.tilt.unmap(specs.center.tilt.default)),
		]),
	]),

	\range,
	IdentityDictionary(know: true).putPairs([
		\pan,	IdentityDictionary(know: true).putPairs([

			\numBox, NumberBox()
			.action_({ |bx|
				// do something with:
				// specs.range.pan.map(bx.value);
			}).value_(specs.range.pan.default),

			\slider, Slider()
			.action_({ |sl| var val;
				val = specs.range.pan.map(sl.value);
				// do something with val
			}).value_(specs.range.pan.unmap(specs.range.pan.default)),
		]),

		\tilt,	IdentityDictionary(know: true).putPairs([

			\numBox, NumberBox()
			.action_({ |bx|
				// do something with:
				// specs.range.pan.map(bx.value);
			}).value_(specs.range.tilt.default),

			\slider, Slider()
			.action_({ |sl| var val;
				val = specs.range.tilt.map(sl.value);
				// do something with val
			}).value_(specs.range.tilt.unmap(specs.range.tilt.default)),
		]),
	]),

	\basePeriod,
	IdentityDictionary(know: true).putPairs([

		\numBox, NumberBox()
		.action_({ |bx|
			// do something with:
			// specs.basePeriod.map(bx.value);
		}).value_(specs.basePeriod.default),

		\slider, Slider()
		.action_({ |sl| var val;
			val = specs.basePeriod.map(sl.value);
			// do something with val
		}).value_(specs.basePeriod.unmap(specs.basePeriod.default)),

		\checkBox, IdentityDictionary(know: true).putPairs([
			\pan, CheckBox(),
			\tilt, CheckBox(),
		]),
	]),

	\basePeriodDiv,
	IdentityDictionary(know: true).putPairs([

		\numBox, NumberBox()
		.action_({ |bx|
			// do something with:
			// specs.basePeriodDiv.map(bx.value);
		}).value_(specs.basePeriodDiv.default),

		\slider, Slider()
		.action_({ |sl| var val;
			val = specs.basePeriodDiv.map(sl.value);
			// do something with val
		}).value_(specs.basePeriodDiv.unmap(specs.basePeriodDiv.default)),

	]),

	\mode,
	IdentityDictionary(know: true).putPairs([
		\random,	Button()
		.states_([["Random", Color.black],["Random", Color.green]]),
		\periodic,	Button()
		.states_([["Periodic", Color.black],["Periodic", Color.green]]),
	])
]);

getLayout = { |spec, numBox, slider, color|
	View().background_(color ?? Color.gray.alpha_(0.1)).layout_(
		VLayout(
			HLayout(
				StaticText().string_(spec.minval).align_('left')
				.stringColor_( Color.black.alpha_(0.5) )
				.fixedWidth_(35), nil,
				numBox.fixedWidth_(35), nil,
				StaticText().string_(spec.maxval).align_('right')
				.stringColor_( Color.black.alpha_(0.5) )
				.fixedWidth_(35),
			),
			slider.orientation_('horizontal')
		)
	)
};

getColor = { |huescl = 0.05, offset = 1 |
	Color.hsv(*baseColor.asHSV
		.put(0, (baseColor.asHSV[0] + huescl).clip(0,1))
		.put(1, baseColor.asHSV[1] * offset ))
};

leftWidth = 60;

win.layout_(
	VLayout(
		// display userview,
		// [
		View().maxHeight_(win.view.bounds.height - win.view.bounds.width)
		.layout_(
			VLayout(
				HLayout(
					controls.mode.periodic, controls.mode.random
				),
				HLayout(
					leftWidth,
					[StaticText().string_("Center"), a: 'center'],
					[StaticText().string_("Range"), a: 'center'],
				),
				HLayout(
					StaticText().string_("Pan").fixedWidth_(leftWidth)
					.stringColor_(Color.black.alpha_(0.8))
					.align_(\center).background_(getColor.(0.05, 0.4)),
					getLayout.( specs.center.pan,
						controls.center.pan.numBox,
						controls.center.pan.slider,
						getColor.(0.05)
					),
					getLayout.( specs.range.pan,
						controls.range.pan.numBox,
						controls.range.pan.slider,
						getColor.(0.05)
					)
				),
				HLayout(
					StaticText().string_("Tilt").fixedWidth_(leftWidth)
					.stringColor_(Color.black.alpha_(0.8))
					.align_(\center).background_(getColor.(0.08, 0.4)),
					getLayout.( specs.center.tilt,
						controls.center.tilt.numBox,
						controls.center.tilt.slider,
						getColor.(0.08)
					),
					getLayout.( specs.range.tilt,
						controls.range.tilt.numBox,
						controls.range.tilt.slider,
						getColor.(0.08)
					)
				),
				15,
				HLayout(
					View().layout_(
						HLayout(
							controls.basePeriod.checkBox.pan, StaticText().string_("Pan"),
							controls.basePeriod.checkBox.tilt, StaticText().string_("Tilt"),
						)
					).maxWidth_(100).background_(getColor.(0.11, 0.4)),
					[StaticText().string_("Base Period"), a: 'center'],
					110
				),

				getLayout.( specs.basePeriod,
					controls.basePeriod.numBox,
					controls.basePeriod.slider,
					getColor.(0.11)
				),

				[StaticText().string_("Period Multiple"), a: 'center'],

				getLayout.( specs.basePeriodDiv,
					controls.basePeriodDiv.numBox,
					controls.basePeriodDiv.slider,
					getColor.(0.11)
				),
			)
		).background_(Color.gray.alpha_(0.1)) //, a: 'bottom']
	).margins_(0)
);

win.refresh;
)