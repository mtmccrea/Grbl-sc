(
d = CtkSynthDef(\fader, { arg outbus, lfoInbus, staticVal=0, staticLag=0.1,
	lfoDex = 0, lfoLag=5, ctlSrcDex=0,
	fadeIn=0.1, ctlFade=5, curve = 0, gate=1;
	var
	env, staticSrc, lfo1, lfo2, lfoSrc, mix, lfoRatio, mixRatio;

	env = EnvGen.kr(Env([1,1,0],[fadeIn,ctlFade],\lin,1), gate, doneAction:0);
	staticSrc = VarLag.kr(staticVal, staticLag, curve);
	#lfo1, lfo2 = [SinOsc.kr(0.1), SinOsc.kr(0.15)];

	lfoRatio = VarLag.kr(lfoDex, lfoLag, curve);

	lfoSrc =
	(lfo1 * (1- lfoRatio)) +
	(lfo2 * lfoRatio);

	mixRatio = VarLag.kr(ctlSrcDex, ctlFade, curve);

	mix =
	(staticSrc * (1- mixRatio)) +
	(lfoSrc * mixRatio);

	Out.kr(outbus, mix * env);

});
)

x = d.note.outbus_(0).play
x.lfoDex_(0)
x.lfoLag_(5)
x.ctlSrcDex_(1)
x.staticVal_(1).ctlSrcDex_(0)


	p = ControlPlotter(0, plotLength: 100, refresh_rate: 20).start;
p.bounds_(-2, 2)