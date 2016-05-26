GrblView {
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

			'streamSizeSl',		Slider().orientation_('horizontal'),
			'streamSizeTxt',	StaticText().string_("size"),

			'streamSumSl', 		Slider().orientation_('horizontal'),
			'streamSumTxt',		StaticText().string_("sum"),
		])
	}

	layItOut {

		win.view.layout_(
			VLayout(
				HLayout(
					widgets.starving, widgets.lagging, widgets.dropping,
				),
				HLayout(
					StaticText().string_("Instruction Queue"),
					widgets.streamSizeSl, widgets.streamSizeTxt,
				),
				HLayout(
					StaticText().string_("Serial Buffer Size"),
					widgets.streamSumSl, widgets.streamSumTxt
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

				\streamSize,{
					widgets.streamSizeSl.value_(inval.linlin(0, 16, 0,1));
					widgets.streamSizeTxt.string_(inval);
				},

				\streamSum,	{
					widgets.streamSumSl.value_(inval.linlin(0, 127, 0,1));
					widgets.streamSumTxt.string_(inval);
				},
			)
			}.defer;
		});
	}
}