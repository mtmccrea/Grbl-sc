GrblPlannerBufView {
	// copyArgs
	var <grbl;
	var <win, <widgets;

	*new { | aGrbl |
		^super.newCopyArgs( aGrbl ).init;
	}

	init {
		grbl.addDependant(this);

		win = Window("Driving Status", Rect(0, 0, 360, 100))
		.onClose_({this.free})
		.front;

		this.makeWidgets;
		this.layItOut;

	}

	makeWidgets {
		widgets = IdentityDictionary(know: true).putPairs([

			'starving', Button().states_([
				["starving", Color.gray, Color.black],
				["starving", Color.red, Color.black]
			]),

			'lagging', Button().states_([
				["lagging", Color.gray, Color.black],
				["lagging", Color.red, Color.black]
			]),

			'dropping', Button().states_([
				["dropping instruction", Color.gray, Color.black],
				["dropping instruction", Color.yellow, Color.black]
			]),

			// 'rxSizeSl',		Slider().orientation_('horizontal'),
			// 'rxSizeTxt',	StaticText().string_("size"),

			'rxSumSl', 		Slider().orientation_('horizontal'),
			'rxSumTxt',		StaticText().string_("sum"),
		])
	}

	layItOut {

		win.view.layout_(
			VLayout(
				HLayout(
					widgets.starving, widgets.lagging, widgets.dropping,
				),

				// NOTE this was actually just the number of _instructions_ waiting in the
				// serial RX buffer, not necessarily what remains in the planning buffer queue.
				// The only thing that matters re: serial rx buffer is how many characters are in queue (128 max)
				// HLayout(
				// 	StaticText().string_("Instruction Queue"),
				// 	widgets.rxSizeSl, widgets.rxSizeTxt,
				// ),
				HLayout(
					StaticText().string_("Serial Rx Size"),
					widgets.rxSumSl, widgets.rxSumTxt
				)
			)
		);
	}

	free {
		grbl.removeDependant(this);
		win !? {win.close};
	}

	update { | who, what ... args |
		var inval;
		inval = args[0];

		if( who == grbl, {
			{
				switch ( what,
					\starving,	{ widgets.starving.value_(inval.asInt) },
					\lagging,	{ widgets.lagging.value_(inval.asInt) },
					\sent,		{ widgets.dropping.value_((inval.asInt - 1).abs) },

					// \rxSize,{
					// 	widgets.rxSizeSl.value_(inval.linlin(0, 16, 0,1));
					// 	widgets.rxSizeTxt.string_(inval);
					// },

					\rxSum,	{
						widgets.rxSumSl.value_(inval.linlin(0, 127, 0,1));
						widgets.rxSumTxt.string_(inval);
					},
				)
			}.defer;
		});
	}
}