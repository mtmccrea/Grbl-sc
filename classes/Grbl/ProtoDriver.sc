// prototype for what needs to be included in a subclass

ProtoDriver : GrblDriver {

	// These defaults are for ultrasound pan/tilt rig
	initSystemParams {

		maxDistPerSec	= 90;	// e.g. 180 / 3.2702541628;	// seconds to go 180 degrees
		maxLagDist		= 10;	// max units that the world can lag behind a control signal
		minFeed			= 50;
		maxFeed			= 4675;
		underDrive		= 1.0;
		overDrive			= 1.1;
		dropLag 			= 0.7;	// if a move is skipped, shorten the next one so it doesn't have to make up the full distance
		// minMoveQueue	= 2;		// min moves queue'd up in buffer
		motorInstructionRate = 45; // rate to send new motor destinations

		// max travel limits, in world coordinates
		// this should correspond to your maxTravelX/Y values in Grbl
		// though this are coordinates, while maxTravelX/Y is a range from limit switches
		xLimitHigh = -5;		// left
		xLimitLow =  -76;	// right
		yLimitHigh = -5;		// top
		yLimitLow = -63;	// bottom
	}

}