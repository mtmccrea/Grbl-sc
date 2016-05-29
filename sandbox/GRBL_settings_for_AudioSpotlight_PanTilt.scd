// settings to use with audio spotlight pan-tilt rigs

// TODO:
// see $10 - status report options
// can set to report planning buffer usage and RX buffer... potentially useful for streaming
// see: - HOMING_FORCE_ORIGIN now sets the origin to the pull-off location,
// rather than where the limit switch was triggered.

$0=10 (step pulse, usec)
$1=25 (step idle delay, msec)
$2=0 (step port invert mask:00000000)
$3=6 (dir port invert mask:00000110)
$4=0 (step enable invert, bool)
$5=0 (limit pins invert, bool)
$6=0 (probe pin invert, bool)
$10=3 (status report mask:00000011)
$11=0.01 (junction deviation, mm)
$12=0.002 (arc tolerance, mm)
$13=0 (report inches, bool)
$14=1 (auto start, bool)
$20=1 (soft limits, bool)
$21=1 (hard limits, bool)
$22=1 (homing cycle, bool)
$23=0 (homing dir invert mask:00000000)
$24=25.000 (homing feed, mm/min) // v0.9i = 550
$25=1500.000 (homing seek, mm/min) // changed from 750 for v0.9i
$26=250 (homing debounce, msec)
$27=25.000 (homing pull-off, mm) // v0.9i = 5.000
$100=444.444 (x, step/mm)
$101=444.444 (y, step/mm)
$102=250.000 (z, step/mm)
$110=4675.000 (x max rate, mm/min)
$111=4675.000 (y max rate, mm/min)
$112=4675.000 (z max rate, mm/min)
$120=85.000 (x accel, mm/sec^2)
$121=85.000 (y accel, mm/sec^2)
$122=85.000 (z accel, mm/sec^2)
$130=292.000 (x max travel, mm)
$131=164.000 (y max travel, mm)
$132=360.000 (z max travel, mm)


a.stateUpdateRate		// introspection, def 8
a.stateUpdateRate = 18	// setting
a.stateUpdateRate = 30

a.motorInstructionRate	// def 40
a.motorInstructionRate = 90
a.motorInstructionRate = 70
a.motorInstructionRate = 60
a.motorInstructionRate = 50
a.motorInstructionRate = 40
a.motorInstructionRate = 20
a.motorInstructionRate = 10

a.maxDegPerSec // def 90
a.maxDegPerSec = 90

a.maxFeed // def 4675
a.maxFeed_(4675)
a.maxFeed_(5000)
a.maxFeed_(3000)

a.minFeed // def 50
a.minFeed_(25)

a.maxAccelX_(85)
a.maxAccelY_(85)

a.send("$11=0.02") // junction dev, def 0.02
a.send("$11=0.002")
a.send("$11=0.06")


a.settings

m.plotter

m.plotterX.stop;
m.plotterY.stop;

m.plotterX.start;
m.plotterY.start;

a.planningBufGui

a.underDrive_(0.8)
a.overDrive_(1.3)
a.dropLag_(0.3)
(
a.underDrive_(1);
a.overDrive_(1);
)

// Pretty good:
a.underDrive = 0.8
a.overDrive	 = 1.3
a.dropLag	 = 0.3
a.minFeed	 = 20
a.maxFeed	 = 4675
a.motorInstructionRate = 20
a.stateUpdateRate = 8
a.maxDegPerSec = 90