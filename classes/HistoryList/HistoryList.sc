/*
Store a List of values that are only added if it is sufficiently
"distant" from the previously stored value, both in cartesian
distance (currently values are duples, interpreted as Points) and time,
i.e. values are only stored if it has been minPeriod seconds since
the last value was stored and minDist from the previous values stored.
If either minDist or minPeriod are nil, that criteria is ignored when
determining whether or not to store the value.

This class was initially created to store a conceivably infinite stream
of values, which we want to access up to a certain time in the past.
E.g. give me the values stored in the last 25 seconds so I can plot the
position of the drawing machine plotter over the last 25 seconds.

HistoryList keeps track of when each value (array of values, actually)
is added to the list. These times are stored in the "times" List
while the values are stored in the "list".

HistoryList has a maxSize, beyond which the times and items lists
within it wrap their pointer. Whenever querying the values above
or below a certain point, the 'times' and 'list' Lists are rotated
so that the write head is back to 0, therefore the lists are sorted
in ascending order again and can be quickly checked for the index
above or below a certain cutoff _time_.
*/

HistoryList {
	var <>minDist, <>minPeriod; // copyArgs
	var <list, <times, startTime, now;
	var wr = 0;//-1; // write head, track position of next written value
	var <initialized=false, listFull=false;
	var <maxSize = 1024;

	*new { |minDist, minPeriod, startNow=false|
		^super.newCopyArgs(minDist, minPeriod).init(startNow);
	}

	init { |startNow|
		list = List();
		times = List();
		startNow.if{
			startTime = Main.elapsedTime;
		};
	}

	prAddFirst { |...values|
		if (startTime.isNil) {
			startTime = Main.elapsedTime;
			now = 0;
		} {
			now = this.now;
		};

		times.add(now);
		postf("\tadding %\n", values);
		list.add(values);
		initialized = true;
	}

	add { |...values|
		var dTest=true, pTest=true;
		block { |break|
			if (initialized) {
				now = this.now;

				// filter time threshold
				if (minPeriod.notNil) {
					if ((now - times.last) < minPeriod) {
						"\ttoo soon".postln;
						pTest = false;
						break.();
					}
				};

				// filter distance threshold
				if (pTest and: minDist.notNil) {
					if (list.last.asPoint.dist(values.asPoint) < minDist) {
						"\ttoo close".postln;
						dTest = false;
						break.();
					}
				};

				if (dTest) {

					/* add a value */
					postf("\tadding %\n", values);

					if (listFull)  {
						times[wr] = now;
						list[wr] = values;
						wr = (wr+1) % maxSize;
					} {
						times.add(now);
						list.add(values);
						listFull = (list.size == maxSize);
						// if (listFull) {wr = 0};
					}
				};

			} {
				this.prAddFirst(*values);
			}
		};
	}

	idxBetween { |st, end|
		^st + (end-st).half.asInt
	}


	// NOTE: the following methods require the items and sizes
	// to be rotated so that their
	indexAfter { |seconds|
		var lowIdx=0, halfIdx, highIdx, prevIdx, idxTime, searching=true, cnt=0, res;

		if (listFull) {
			// rotate so pointer is as 0
			list = list.rotate(wr.neg);
			times = times.rotate(wr.neg);
			wr = 0;
		};

		highIdx = times.size-1;

		// first test last item
		if (times[highIdx] <= seconds) {
			^nil
		};

		while ( {
			searching
		},{
			halfIdx = this.idxBetween(lowIdx, highIdx);

			postf("halfIdx: %, %\n", halfIdx, times[halfIdx]);

			idxTime = times[halfIdx];

			// #lowIdx, highIdx = [prevIdx, halfIdx].sort;
			if (idxTime < seconds) {
				// landed below threshold
				highIdx = [lowIdx, highIdx].maxItem;
				lowIdx = halfIdx;
			} { // landed above threshold
				lowIdx = [lowIdx, highIdx].minItem;
				highIdx = halfIdx;
			};

			if (halfIdx == prevIdx) {
				searching = false;
				// see which side the index falls on
				res = if(times[halfIdx] >= seconds) {
					halfIdx
				} {
					halfIdx + 1
				}
			} {
				prevIdx = halfIdx;
				cnt = cnt+1;
			}
		}
		);

		^res
	}

	indexBefore { |seconds|
		var lowIdx=0, halfIdx, highIdx, prevIdx, idxTime, searching=true, cnt=0, res;

		seconds.isNegative.if {
			"Time provided is negative".throw
		};

		if (listFull) {
			// rotate so pointer is as 0
			list = list.rotate(wr.neg);
			times = times.rotate(wr.neg);
			wr = 0;
		};

		highIdx = times.size-1;

		// first test last item
		if (times[highIdx] <= seconds) {
			^highIdx
		};

		while ({
			searching
		},{
			halfIdx = this.idxBetween(lowIdx, highIdx);

			postf("halfIdx: %, %\n", halfIdx, times[halfIdx]);

			idxTime = times[halfIdx];

			// #lowIdx, highIdx = [prevIdx, halfIdx].sort;
			if (idxTime < seconds) {
				// landed below threshold
				highIdx = [lowIdx, highIdx].maxItem;
				lowIdx = halfIdx;
			} { // landed above threshold
				lowIdx = [lowIdx, highIdx].minItem;
				highIdx = halfIdx;
			};

			if (halfIdx == prevIdx) {
				searching = false;
				// see which side the index falls on
				res = if(times[halfIdx] <= seconds) {
					halfIdx
				} {
					halfIdx - 1
				}
			} {
				prevIdx = halfIdx;
				cnt = cnt+1;
			}
		}
		);

		^res
	}

	itemsBefore { |seconds|
		var idx;
		idx = this.indexBefore(seconds);

		// debug
		postf("times before %:\n", seconds);
		times[..idx].do(_.postln);

		^list[..idx]
	}

	itemsAfter { |seconds|
		var idx;
		idx = this.indexAfter(seconds);

		// debug
		postf("times after %:\n", seconds);
		times[idx..].do(_.postln);

		^if (idx.isNil) {[]} {list[idx..]};
	}

	// last items within 'seconds' from last recorded
	itemsFromLastRecorded { |seconds|
		^this.itemsAfter(this.maxTime - seconds)
	}

	// last items within 'seconds' from "now"
	itemsWithinLast { |seconds|
		var n;
		n = this.now;
		if (n.notNil) {
			^this.itemsAfter(now - seconds)
		} {
			^[]
		}
	}

	now {
		if (startTime.isNil) {
			"startTime initialized".warn;
			^nil
		} {
			^Main.elapsedTime - startTime
		}
	}

	offsetIdx { |idx|
		if (listFull) {
			^(idx + wr) % maxSize
		} {
			^idx
		};
	}

	at { |index| ^list[ this.offsetIdx(index) ] }
	last { ^list[ this.offsetIdx(list.size-1) ] }
	first { ^list[ this.offsetIdx(0) ] }
	timeAt { |index| ^times[ this.offsetIdx(index) ] }
	size  { ^times.size }
	maxTime { ^times[ this.offsetIdx(times.size-1) ] }

	clear { |startNow=false|
		times = List();
		list = List();
		if (startNow) {
			startTime = Main.elapsedTime;
		} {
			startTime = nil;
		};
		initialized = false;
		wr = 0; //-1;
		listFull = false;
	}

	// TODO: atm, maxSize requires some care if changing
	// on the fly, so require clearing the list first for now
	maxSize_ { |size|
		if (initialized) {
			Error("Must .clear the HistoryList before setting maxSize").errorString.postln;
		} {
			maxSize = size;
		};
	}
}

/*HistoryList {
	var <>minDist, <>minPeriod; // copyArgs
	var <list, <times, startTime, now;
	var <initialized=false;

	*new { |minDist, minPeriod, startNow=false|
		^super.newCopyArgs(minDist, minPeriod).init(startNow);
	}

	init { |startNow|
		list = List();
		times = List();
		startNow.if{
			startTime = Main.elapsedTime;
		};
	}

	prAddFirst { |...values|
		if (startTime.isNil) {
			startTime = Main.elapsedTime;
			now = 0;
		} {
			now = this.now;
		};

		times.add(now);
		postf("\tadding %\n", values);
		list.add(values);
		initialized = true;
	}

	add { |...values|
		var dTest=true, pTest=true;
		block { |break|
			if (initialized) {
				now = this.now;

				// filter time threshold
				if (minPeriod.notNil) {
					if ((now - times.last) < minPeriod) {
						"\ttoo soon".postln;
						pTest = false;
						break.();
					}
				};

				// filter distance threshold
				if (pTest and: minDist.notNil) {
					if (list.last.asPoint.dist(values.asPoint) < minDist) {
						"\ttoo close".postln;
						dTest = false;
						break.();
					}
				};

				if (dTest) {
					times.add(now);
					postf("\tadding %\n", values);
					list.add(values);
				};

			} {
				this.prAddFirst(*values);
			}
		};
	}

	idxBetween { |st, end|
		^st + (end-st).half.asInt
	}

	indexAfter { |seconds|
		var lowIdx=0, halfIdx, highIdx, prevIdx, idxTime, searching=true, cnt=0, res;

		highIdx = times.size-1;

		// first test last item
		if (times[highIdx] <= seconds) {
			^nil
		};

		while ( {
			searching
		},{
			halfIdx = this.idxBetween(lowIdx, highIdx);

			postf("halfIdx: %, %\n", halfIdx, times[halfIdx]);

			idxTime = times[halfIdx];

			// #lowIdx, highIdx = [prevIdx, halfIdx].sort;
			if (idxTime < seconds) {
				// landed below threshold
				highIdx = [lowIdx, highIdx].maxItem;
				lowIdx = halfIdx;
			} { // landed above threshold
				lowIdx = [lowIdx, highIdx].minItem;
				highIdx = halfIdx;
			};

			if (halfIdx == prevIdx) {
				searching = false;
				// see which side the index falls on
				res = if(times[halfIdx] >= seconds) {
					halfIdx
				} {
					halfIdx + 1
				}
			} {
				prevIdx = halfIdx;
				cnt = cnt+1;
			}
		}
		);

		^res
	}

	indexBefore { |seconds|
		var lowIdx=0, halfIdx, highIdx, prevIdx, idxTime, searching=true, cnt=0, res;

		seconds.isNegative.if {
			"Time provided is negative".throw
		};

		highIdx = times.size-1;

		// first test last item
		if (times[highIdx] <= seconds) {
			^highIdx
		};

		while ({
			searching
		},{
			halfIdx = this.idxBetween(lowIdx, highIdx);

			postf("halfIdx: %, %\n", halfIdx, times[halfIdx]);

			idxTime = times[halfIdx];

			// #lowIdx, highIdx = [prevIdx, halfIdx].sort;
			if (idxTime < seconds) {
				// landed below threshold
				highIdx = [lowIdx, highIdx].maxItem;
				lowIdx = halfIdx;
			} { // landed above threshold
				lowIdx = [lowIdx, highIdx].minItem;
				highIdx = halfIdx;
			};

			if (halfIdx == prevIdx) {
				searching = false;
				// see which side the index falls on
				res = if(times[halfIdx] <= seconds) {
					halfIdx
				} {
					halfIdx - 1
				}
			} {
				prevIdx = halfIdx;
				cnt = cnt+1;
			}
		}
		);

		^res
	}

	itemsBefore { |seconds|
		var idx;
		idx = this.indexBefore(seconds);
		^list[..idx]
	}

	itemsAfter { |seconds|
		var idx;
		idx = this.indexAfter(seconds);
		^if (idx.isNil) {[]} {list[idx..]};
	}

	// last items within 'seconds' from last recorded
	itemsFromLastRecorded { |seconds|
		^this.itemsAfter(this.maxTime - seconds)
	}

	// last items within 'seconds' from "now"
	itemsWithinLast { |seconds|
		var n;
		n = this.now;
		if (n.notNil) {
			^this.itemsAfter(now - seconds)
		} {
			^[]
		}
	}

	now {
		if (startTime.isNil) {
			"startTime initialized".warn;
			^nil
		} {
			^Main.elapsedTime - startTime
		}
	}

	at { |index| ^list[index] }
	last { ^list.last }
	first { ^list[0] }
	timeAt { |index| ^times[index] }
	size  { ^times.size }
	maxTime { ^times.last }

	clear { |startNow=false|
		times = List();
		list = List();
		if (startNow) {
			startTime = Main.elapsedTime;
		} {
			startTime = nil;
		};
		initialized = false;
	}
}*/

/*  Testing
h = HistoryList(0.3, 0.3);

h.minPeriod = 0.3;
h.minDist = 0.3;

h.minPeriod = nil;
h.minDist = nil;

h.initialized

(
f = fork {

	inf.do{
		var vals;
		"adding ".post;
		vals = [rrand(0.1, 0.7), rrand(0.1, 0.7)].postln;
		h.add(*vals);
		rrand(0.1, 0.7).wait
	}
}
)

f.stop

h.list
h.times
h.size
h.maxTime
h.now
*/