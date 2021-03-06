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
is added to the list. These times are stored in the ".times" List
while the values are stored in the ".list".

HistoryList has a maxSize, beyond which the .times and .items lists
within it wrap their pointer. Whenever querying the values above
or below a certain point, the 'times' and 'list' Lists are rotated
so that the write head is back to 0, therefore the lists are sorted
in ascending order again and can be quickly checked for the index
above or below a certain cutoff _time_. As such the items in the list
can't be expected to be in the same index all the time, which is why
.at, .last, .first, .timeAt are all reimplemented to account for the
constant rotating.
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
		//postf("\tadding %\n", values);
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
						//"\ttoo soon".postln;
						pTest = false;
						break.();
					}
				};

				// filter distance threshold
				if (pTest and: minDist.notNil) {
					if (list.last.asPoint.dist(values.asPoint) < minDist) {
						//"\ttoo close".postln;
						dTest = false;
						break.();
					}
				};

				if (dTest) {
					/* add a value */
					// postf("\tadding %\n", values);

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
		^st + (end-st).half.asInteger
	}


	// NOTE: the following methods require the items and sizes
	// to be rotated so that their
	indexAfter { |seconds|
		var lowIdx = 0, halfIdx, highIdx, prevIdx, idxTime;
		var searching = true, cnt = 0, res;

		if (listFull) {
			// rotate so pointer is as 0
			list = list.rotate(wr.neg);
			times = times.rotate(wr.neg);
			wr = 0;
		};

		highIdx = times.size-1;

		// first test last item
		if (times[highIdx] <= seconds) { ^nil };

		while ( {
			searching
		},{
			halfIdx = this.idxBetween(lowIdx, highIdx);

			// postf("halfIdx: %, %\n", halfIdx, times[halfIdx]);

			idxTime = times[halfIdx];

			// #lowIdx, highIdx = [prevIdx, halfIdx].sort;
			if (idxTime < seconds) {
				// landed below threshold
				highIdx = [lowIdx, highIdx].maxItem;
				lowIdx = halfIdx;
			}{
				// landed above threshold
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
			}{
				prevIdx = halfIdx;
				cnt = cnt + 1;
			}
		}
		);

		^res
	}

	// rotate the list and times so that the pointer is at 0
	// and the list is ascending from earliest stored time
	rotateToZero {
		list = list.rotate(wr.neg);
		times = times.rotate(wr.neg);
		wr = 0;
	}

	indexBefore { |seconds|
		var lowIdx = 0, halfIdx, highIdx, prevIdx, idxTime;
		var searching = true, cnt = 0, res;

		seconds.isNegative.if {
			"Time provided is negative".throw
		};

		if (listFull) {this.rotateToZero};

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
		var idx = this.indexBefore(seconds);
		^list[..idx]
	}

	itemsAfter { |seconds|
		var idx;
		idx = this.indexAfter(seconds);
		^if (idx.isNil) { [] } { list[idx..] };
	}

	// last items within 'seconds' from last recorded
	itemsFromLastRecorded { |seconds|
		^this.itemsAfter(this.maxTime - seconds)
	}

	// last items within 'seconds' from "now"
	itemsWithinLast { |seconds|
		var n = this.now;
		^if (n.notNil) {
			this.itemsAfter(now - seconds)
		}{
			[]
		}
	}

	now {
		^if (startTime.isNil) {
			"startTime uninitialized".warn;
			nil
		}{
			Main.elapsedTime - startTime
		}
	}

	offsetIdx { |idx|
		^if (listFull) {
			(idx + wr) % maxSize
		} {
			idx
		};
	}

	at      { |index| ^list[this.offsetIdx(index)] }
	last    { ^list[this.offsetIdx(list.size-1)] }
	first   { ^list[this.offsetIdx(0)] }
	timeAt  { |index| ^times[this.offsetIdx(index)] }
	size    { ^times.size }
	maxTime { ^times[this.offsetIdx(times.size-1)] }

	clear { |startNow=false|
		times = List();
		list = List();
		if (startNow) {
			startTime = Main.elapsedTime;
		} {
			startTime = nil;
		};
		initialized = false;
		wr = 0;
		listFull = false;
	}

	// TODO: atm, maxSize requires some care if changing
	// on the fly, so require clearing the list first for now
	maxSize_ { |size|
		if (initialized) {
			Error(
				"Must .clear the HistoryList before setting maxSize"
			).errorString.postln;
		}{
			maxSize = size;
		};
	}
}

/*  Testing
h = HistoryList(0.1, 0.1);

h.maxSize = 12

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
		rrand(0.1, 0.4).wait
	}
}
)

f.stop

h.now
h.size
h.list
h.times
h.size
h.maxTime
h.now

h.clear

h.times.size
h.list.size
h.times.last

h.times[19..]
h.times[20]

a = h.itemsBefore(10)
a = h.itemsAfter(40)

h.times[h.indexBefore(15)]
h.times[h.indexAfter(15)]

a = h.itemsBefore(25)
h.times[h.indexBefore(0)]
h.indexAfter(12)
h.times[h.indexAfter(12)]
*/