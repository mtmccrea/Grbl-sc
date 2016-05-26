(
r = Routine.run({
	var cnt = 0, longDex, shortDex, rateMultiple;

	rateMultiple = 4;

	inf.do{
		cnt = cnt +1;
		longDex = cnt.fold(0, rateMultiple);
		shortDex = cnt % 2;
		(longDex == 0).if{cnt == 0};

		postf("cnt %\tlongDex %\tshortDex %\n", cnt, longDex, shortDex);

		1.wait;
	}
})
)

r.stop
r.reset.play

getBounds = { |whichDex|
	[[xLo, xHi],[yLo, yHi]].at(whichDex);
};

drivePeriodic = { |basePeriod = 10, baseAxis = 0, rateMultiple = 1|
	var cnt = 0, slowRamp, fastRamp;

	newDestPeiod = basePeriod / rateMultiple * 0.5;

	r = Routine.run({
		var cnt = 0, longDex, shortDex;

		inf.do{
			cnt = cnt + 1;
			slowDex = cnt.fold(0, rateMultiple); // climbs and descends ramp
			fastDex = cnt % 2; // toggle 0/1
			(longDex == 0).if{cnt == 0};

			postf("cnt %\tlongDex %\tshortDex %\n", cnt, longDex, shortDex);

			slowDest = slowDex.linlin(0, rateMultiple, *getBounds.(baseAxis))
			fastDest = getBounds.((baseAxis-1).abs).at(fastDex);

			destsOrdered = [slowDest, fastDest].rotate(baseAxis);

			a.goTo_(destsOrdered[0], destsOrdered[1], feed)

			newDestPeiod.wait;
		}
	})
}



}