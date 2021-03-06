/*
This is an archive of past methods of ArduinoGRBL
that experimented with automated movement.
Kept here for reference.
*/

/*
	An attempt to create a buffer that filled instructions
	in advance of where the motor is.  Doesn't work all that well...
	*/
	followWithLeadingBuffer_ { |ctlFade, driveRate, instructionLag = 4|
		var iBuf, iBufSize, iDex, iBufState;

		stateRoutine.isPlaying.not.if{ this.updateState_(true) };

		lastDest	?? { lastDest = wPos[0] };
		driveRate	!? { motorInstructionRate = driveRate };

		iBuf = List(instructionLag * 3); // instruction buffer
		iBufSize = instructionLag * 3;
		iDex = 0;			// instruction index
		iBufState = 0;	// states:	0 - filling the initial buffer
								//				1 - buffer is full and instructions are cycling


		followRoutine = Routine.run( {
			var maxNextDist, catchup = 1, ctlBufferSize, ctlBuffer, ctlPtr = 0;

			inf.do{ |i|
				if(mode != "Alarm", {

					// poll the control bus for a new position
					ctlFade.controlBus.get({ |busnum, goTo|
						var deltaFromWorld, deltaFromLast, nextPos, nextFeed;

						maxNextDist		= maxDegPerSec / motorInstructionRate;
						//maxNextDist		= maxNextDist * 1.2; // let it overshoot a bit if needed

						deltaFromWorld	= goTo - wPos[0]; // distance from the latest world position
						deltaFromLast	= goTo - lastDest; // distance from the last scheduled destination

						// is the instruction buffer starving ?
						starving = (streamBuf.size < minMoveQueue);

						// is it lagging too far behind the world?
						(deltaFromWorld > maxLagDist).if(
							{ lagging = true; debug.if{ "world is LAGGING".postln} },
							{ lagging = false }
						);

						if( deltaFromLast.abs > maxNextDist,
							// is it trying to go farther than a maximum single drive instruction?
							{
								// limit the next instruction to a max rate and distance
								nextPos = lastDest + (maxNextDist * deltaFromLast.sign);
								nextFeed = maxFeed;
								"Exceeding max move - limiting next destination".postln;
							},{

								// a regular destination and feed
								nextPos = goTo;
								nextFeed = feedSpec.map(deltaFromLast.abs/maxNextDist);

								if( lagging,
									// instruct to move further, bump up the next feed rate, clipping at max feed
									{
										// nextPos = lastDest + (deltaFromLast * 1.3);
										nextFeed = [(nextFeed * 1.2), maxFeed].minItem; // clip at maxFeed
									},
									// slow down if the instruction list is starving
									{
										if( starving, {
											// nextFeed = nextFeed * 0.8;
											// catchup = catchup * 1.05;
										}, { catchup = 1 });
									}
								);
						});

						switch( iBufState,
							// buffer full, cycling through
							1, {
								iDex.post; " - filling".postln; // debug
								iBuf.put( iDex, [nextPos, nextFeed]);

								if( ( ((iDex+1) % instructionLag) == 0 ), {
									var sendStart;
									sendStart = (iDex+1 - instructionLag).wrap(0, instructionLag*3-1);
									// send instruction set of last instructionLag number of new instructions
									this.sendInstructions( iBuf[sendStart..(sendStart + instructionLag - 1)] );

									// debug
									postf("sending instruction set - iDex: % sendStart: %\n", iDex, sendStart);
									iBuf[sendStart..(sendStart + instructionLag - 1)].do(_.postln;);

									// debug
									postf("updating las pos:\t%\n", lastDest );
									}
								);

							},
							// initial filling of instruction buffer
							// When follow is called, there is a (instructionLag*2*driveRate.reciprocal) second delay
							0,	{
								iDex.post; " - adding - ".post; [nextPos, nextFeed].postln; //debug
								iBuf.add( [nextPos, nextFeed]);

								case
								{iBuf.size == (instructionLag*2)} {
									// send first 2 instruction sets
									this.sendInstructions( iBuf[0..(instructionLag*2-1)] );
								}

								{iBuf.size == (instructionLag*3-1)} {
									// add a dummy entry which will be overwritten next iteration above
									iBuf.add( [nextPos, nextFeed]);
									// flip state to change into cycling mode
									iBufState = 1;
								};
							}
						);

						lastDest = nextPos;
						iDex = (iDex + 1) % iBufSize;
					});

					// rate to send G-code positions to GRBL
					// (motorInstructionRate * catchup).reciprocal.wait
					motorInstructionRate.reciprocal.wait

					},{"won't follow in ALARM mode".postln; 0.25.wait;}
				)
			}
		});
	}

	sendInstructions { |coordFeedArr|
		fork{ coordFeedArr.do{ | destFeed |
			"instructing: ".post; destFeed.postln;
			this.x_(*destFeed);
			// ideally count characters and ok responses then send the next instruction
			0.025.wait;
		} };
	}





// this version of follow_ works in a basic way
follow_ { |ctlFade, driveRate|

	stateRoutine.isPlaying.not.if{ this.updateState_(true) };

	lastDest	?? { lastDest = wPos[0] };
	driveRate	!? { motorInstructionRate = driveRate };

	followRoutine = Routine.run( {
		var maxNextDist, catchup = 1;

		inf.do{
			if(mode != "Alarm", {

				// NOTE:	change this buffer value retrieving message to a SendReply scheme
				// 			from a running synth that polls the bus values
				// poll the control bus for a new position
				ctlFade.controlBus.get({ |busnum, goTo|
					var deltaFromWorld, deltaFromLast, nextPos, nextFeed;

					maxNextDist	 = maxDegPerSec / motorInstructionRate;

					// distance from the latest world position
					deltaFromWorld = goTo - wPos[0];
					// distance from the last scheduled destination
					deltaFromLast = goTo - lastDest;

					debug.if{
						postf("world\t\t\t%", wPos[0]);
						postf("lastDest\t\t%", lastDest);
						postf("goTo\t\t\t%", goTo);
						postf("maxNextDist\t\t%", maxNextDist);
						postf("deltaFromWorld\t%", deltaFromWorld);
						postf("deltaFromLast\t%\n", deltaFromLast);
						postf( "streamBuf\t%\n\n", streamBuf );
					};

					// is the instruction buffer starving ?
					starving = (streamBuf.size < minMoveQueue);
					this.changed(\starving, starving);

					// is it lagging too far behind the world?
					lagging = (deltaFromWorld > maxLagDist);
					this.changed(\lagging, lagging);

					if( deltaFromLast.abs > maxNextDist,
						// is it trying to go farther than a maximum single drive instruction?
						{
							// limit the next instruction to a max rate and distance
							nextPos = lastDest + (maxNextDist * deltaFromLast.sign);
							nextFeed = maxFeed;
							// "Exceeding max move - limiting next destination".postln;
						},{

							// a regular destination and feed
							nextPos = goTo;
							nextFeed = feedSpec.map(deltaFromLast.abs/maxNextDist);

							if( lagging,
								{	// instruct to move further, bump up the next feed rate,
									// clipping at max feed
									nextFeed = [(nextFeed * 1.2), maxFeed].minItem;
								},
								// slow down if the instruction list is starving
								{
									if( starving, {
										// nextFeed = nextFeed * 0.8;
										// "STARVING - slowing down the feed".postln;
										// catchup = catchup * 1.05;
									}, { catchup = 1 });
								}
							);
					});

					// [busnum, goTo].postln
					// ~az = goTo.postln;
					this.x_(nextPos.round(0.1), nextFeed.round(1));

					// debug
					postf("next move/feed:\t%\n\n\n", [nextPos, nextFeed]);

					lastDest = nextPos;
				});

				// rate to send G-code positions to GRBL
				// (motorInstructionRate * catchup).reciprocal.wait
				motorInstructionRate.reciprocal.wait

			},{"won't follow in ALARM mode".postln;}
			)
		}
	});
}


// follow a control bus (ControlFade), polling it as often as is needed to keep the
// serial buffer full
followSerialBuf_ { |aControlBus, driveRate, updateStateHz = 5, gui = false|

	aControlBus ?? {"Must provide a control bus to follow".error};

	// write the position of the motor to a bus
	// not necessary?? - this is used for plotting
	//writePos.not.if{ this.writePosToBus_(true) };

	// monitor how full the serial buffer is: starved or dropping messages
	gui.if{ followView = ArduinoGRBLView(this) };

	// start the stream buffer fresh
	streamBuf.clear;

	// make sure stateRoutine is playing to update wPos and state variables
	stateRoutine.isPlaying.not.if{
		// Recommended not to exceed 5Hz update rate
		this.updateState_(true, updateStateHz);
	};

	// ensure lastDest has a value
	lastDest	?? { lastDest = wPos[0] };
	// forward driveRate to motorInstructionRate, which is used for waittime below
	driveRate	!? { motorInstructionRate = driveRate };

	// start the synth that forwards the driving control signal to the lang
	server ?? {server = Server.default};
	server.waitForBoot({

		followSynth.isNil.if(
			{
				var followDef;
				followDef = CtkSynthDef( \busFollow++id, {// make the def unique w/ id
					| followBus, sendRate|
					SendReply.ar(
						Impulse.ar(sendRate),
						'/busFollow'++id,
						In.kr(followBus, 1)
					)
				});
				server.sync;

				followSynth = followDef.note
				.followBus_(aControlBus)
				.sendRate_(motorInstructionRate)
				.play
			},{
				followSynth
				.followBus_(aControlBus)
				.sendRate_(motorInstructionRate)
				.run;
			}
		);
	});

	// create a responder to receive the driving control signal from the server
	followResponder ?? {
		followResponder = OSCFunc({
			|msg, time, addr, recvPort|

			var goTo, maxNextDist, sent, catchup = 1;
			var deltaFromWorld, deltaFromLast, nextPos, nextFeed;

			if(mode != "Alarm", {

				// the target destination from the control sig
				goTo = msg[3];

				maxNextDist	= maxDegPerSec / motorInstructionRate;

				// distance from...
				deltaFromWorld	= goTo - wPos[0];  // the latest world position
				deltaFromLast	= goTo - lastDest; // the last scheduled destination

				debug.if{
					postf("world\t\t\t%", wPos[0]);
					postf("lastDest\t\t%", lastDest);
					postf("goTo\t\t\t%", goTo);
					postf("maxNextDist\t\t%", maxNextDist);
					postf("deltaFromWorld\t%", deltaFromWorld);
					postf("deltaFromLast\t%\n", deltaFromLast);
					postf( "streamBuf\t%\n\n", streamBuf );
				};

				// is the instruction buffer starving ?
				starving = (streamBuf.size < minMoveQueue);
				this.changed(\starving, starving);

				// is it lagging too far behind the world?
				lagging = (deltaFromWorld > maxLagDist);
				this.changed(\lagging, lagging);

				// is it lagging too far behind the world?
				(deltaFromWorld > maxLagDist).if(
					{ lagging = true; debug.if{"world is LAGGING".postln} },
					{ lagging = false }
				);

				if( deltaFromLast.abs > maxNextDist,
					// is it trying to go farther than a maximum single drive instruction?
					{
						// limit the next instruction to a max rate and distance
						nextPos = lastDest + (maxNextDist * deltaFromLast.sign);
						nextFeed = maxFeed;
						debug.if{"Exceeding max move - limiting next destination".postln};
					},{

						// a regular destination and feed
						nextPos = goTo;
						nextFeed = feedSpec.map(deltaFromLast.abs/maxNextDist);

						if( lagging,
							// instruct to move further, bump up the next feed rate, clipping at max feed
							{
								// nextPos = lastDest + (deltaFromLast * 1.3);
								nextFeed = [(nextFeed * 1.2), maxFeed].minItem;
							},
							// // slow down if the instruction list is starving
							// {
							// 	if( starving, {
							// 		// nextFeed = nextFeed * 0.8;
							// 		// "STARVING - slowing down the feed".postln;
							// 		// catchup = catchup * 1.05;
							// 	}, { catchup = 1 });
							// }
						);
				});

				// try to send the move, returns false if no room in buffer
				sent = this.submitMove(nextPos.round(0.001), nextFeed.round(1));

				if( sent,
					{
						lastDest = nextPos;
						this.changed(\sent, 1);

						debug.if{
							postf( "move sent, RX BUFSIZE:\t%\n", streamBuf.sum);
							postf( "next move/feed:\t%\n\n\n", [nextPos, nextFeed]);
						};
					},{
						debug.if{ "skipping move".postln };
						this.changed(\sent, 0);
					}
				);

			},{ "won't follow in ALARM mode".postln }
			)
		},
		'/busFollow'++id // the OSC path
		);
	}
}

// used by followSerialBuf_
submitMove { |toX, feed|
	var cmd, size;

	cmd 	= "G01";

	toX		!? {cmd = cmd ++ "X" ++ toX};
	// toY	!? {cmd = cmd ++ "Y" ++ toY};
	feed	!? {cmd = cmd ++ "F" ++ feed};
	size 	= cmd.size + 1; // add 1 for the eol char added in .send

	// only send the move if the message won't
	// overflow the serial buffer buffer
	if ( (size + streamBuf.sum) < rx_buf_size,
		{ this.send(cmd); ^true },{^false}
	);
}



// Routine-based version, instead of OSC version
// follw a control bus (ControlFade), polling it as often as is needed to keep the
// serial buffer full
followSerialBuf_ { |aControlBus, driveRate, gui = false|

	// write the position of the motor to a bus
	// not necessary?? - this is used for plotting
	//writePos.not.if{ this.writePosToBus_(true) };

	// monitor how full the serial buffer is: starved or dropping messages
	gui.if{ followView = ArduinoGRBLView(this) };

	// start the stream buffer fresh
	streamBuf.clear;

	// make sure stateRoutine is playing to update wPos and state variables
	stateRoutine.isPlaying.not.if{
		// Recommended not to exceed 5Hz update rate
		this.updateState_(true, 5);
	};

	// ensure lastDest has a value
	lastDest	?? { lastDest = wPos[0] };
	// forward driveRate to motorInstructionRate, which is used for waittime below
	driveRate	!? { motorInstructionRate = driveRate };

	// make sure the routine hasn't already been started
	followRoutine !? {followRoutine.stop};

	followRoutine = Routine.run( {
		var maxNextDist, sent, catchup = 1;

		inf.do{
			if(mode != "Alarm", {
				// poll the control bus for a new position
				aControlBus.get({ |busnum, goTo|
					var deltaFromWorld, deltaFromLast, nextPos, nextFeed;

					maxNextDist		= maxDegPerSec / motorInstructionRate;

					// distance from the latest world position
					deltaFromWorld	= goTo - wPos[0];
					// distance from the last scheduled destination
					deltaFromLast	= goTo - lastDest;

					debug.if{
						postf("world\t\t\t%", wPos[0]);
						postf("lastDest\t\t%", lastDest);
						postf("goTo\t\t\t%", goTo);
						postf("maxNextDist\t\t%", maxNextDist);
						postf("deltaFromWorld\t%", deltaFromWorld);
						postf("deltaFromLast\t%\n", deltaFromLast);
						postf( "streamBuf\t%\n\n", streamBuf );
					};

					// is the instruction buffer starving ?
					starving = (streamBuf.size < minMoveQueue);
					this.changed(\starving, starving);

					// is it lagging too far behind the world?
					lagging = (deltaFromWorld > maxLagDist);
					this.changed(\lagging, lagging);

					// is it lagging too far behind the world?
					(deltaFromWorld > maxLagDist).if(
						{ lagging = true; debug.if{"world is LAGGING".postln} },
						{ lagging = false }
					);

					if( deltaFromLast.abs > maxNextDist,
						// is it trying to go farther than a maximum single drive instruction?
						{
							// limit the next instruction to a max rate and distance
							nextPos = lastDest + (maxNextDist * deltaFromLast.sign);
							nextFeed = maxFeed;
							debug.if{"Exceeding max move - limiting next destination".postln};
						},{

							// a regular destination and feed
							nextPos = goTo;
							nextFeed = feedSpec.map(deltaFromLast.abs/maxNextDist);

							if( lagging,
								// instruct to move further, bump up the next feed rate, clipping at max feed
								{
									// nextPos = lastDest + (deltaFromLast * 1.3);
									nextFeed = [(nextFeed * 1.2), maxFeed].minItem;
								},
								// // slow down if the instruction list is starving
								// {
								// 	if( starving, {
								// 		// nextFeed = nextFeed * 0.8;
								// 		// "STARVING - slowing down the feed".postln;
								// 		// catchup = catchup * 1.05;
								// 	}, { catchup = 1 });
								// }
							);
					});

					// try to send the move, returns false if no room in buffer
					sent = this.submitMove(nextPos.round(0.1), nextFeed.round(1));

					if( sent,
						{
							lastDest = nextPos;
							this.changed(\sent, 1);

							debug.if{
								postf( "move sent, RX BUFSIZE:\t%\n", streamBuf.sum);
								postf( "next move/feed:\t%\n\n\n", [nextPos, nextFeed]);
							};
						},{
							debug.if{ "skipping move".postln };
							this.changed(\sent, 0);
						}
					);


				});

				// rate to send G-code positions to GRBL
				// (motorInstructionRate * catchup).reciprocal.wait
				motorInstructionRate.reciprocal.wait

			},{"won't follow in ALARM mode".postln;}
			)
		}
	});
}