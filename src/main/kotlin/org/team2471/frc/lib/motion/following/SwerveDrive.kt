package org.team2471.frc.lib.motion.following

import com.team254.lib.util.Interpolable
import com.team254.lib.util.InterpolatingDouble
import com.team254.lib.util.InterpolatingTreeMap
import edu.wpi.first.wpilibj.Timer
import org.team2471.frc.lib.coroutines.delay
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.math.Vector2
import org.team2471.frc.lib.motion.following.SwerveDrive.Companion.prevTranslationInput
import org.team2471.frc.lib.motion.following.SwerveDrive.Companion.prevTurn
import org.team2471.frc.lib.motion_profiling.Path2D
import org.team2471.frc.lib.motion_profiling.following.SwerveParameters
import org.team2471.frc.lib.units.*
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

private val poseHistory = InterpolatingTreeMap<InterpolatingDouble, SwerveDrive.Pose>(75)
private var prevPosition = Vector2(0.0, 0.0)
private var prevPathPosition = Vector2(0.0, 0.0)
private var prevTime = 0.0
private var prevPathHeading = 0.0.radians
private val MAXHEADINGSPEED_DEGREES_PER_SECOND = 600.0
private var prevHeadingError = 0.0.degrees

interface SwerveDrive {
    val parameters: SwerveParameters
    var heading: Angle
    val headingRate: AngularVelocity
    var position: Vector2
    var velocity: Vector2
    var robotPivot: Vector2 // location of rotational pivot in robot coordinates
    var headingSetpoint: Angle

    val modules: Array<Module>

    fun startFollowing() = Unit

    fun stopFollowing() = Unit

    interface Module {
        // module fixed parameters
        val modulePosition: Vector2 // coordinates of module in robot coordinates
        val angleOffset: Angle

        // encoder interface
        val angle: Angle
        val speed: Double
        val currDistance: Double
        var prevDistance: Double

        // motor interface
        var angleSetpoint: Angle

        fun setDrivePower(power: Double)

        fun stop()
        fun zeroEncoder()
        fun driveWithDistance(angle: Angle, distance: Length)
    }

    companion object {
        var prevTranslationInput = Vector2(0.0,0.0)
        var prevTurn = 0.0
    }

    data class Pose(val position: Vector2, val heading: Angle) : Interpolable<Pose> {
        override fun interpolate(other: Pose, x: Double): Pose = when {
            x <= 0.0 -> this
            x >= 1.0 -> other
            else -> Pose(position.interpolate(other.position, x), (other.heading - heading) * x + heading)
        }
    }
}

val SwerveDrive.pose: SwerveDrive.Pose
    get() = SwerveDrive.Pose(position, heading)

fun SwerveDrive.lookupPose(time: Double): SwerveDrive.Pose = poseHistory.getInterpolated(InterpolatingDouble(time))

fun SwerveDrive.stop() {
    for (module in modules) {
        module.stop()
    }
}

fun SwerveDrive.zeroEncoders() {
    for (module in modules) {
        module.zeroEncoder()
    }
    position = Vector2(0.0, 0.0)
}


fun SwerveDrive.drive(
    translation: Vector2,
    turn: Double,
    fieldCentric: Boolean = true,
    teleopClosedLoopHeading: Boolean = false,
    softTranslation: Vector2 = Vector2(0.0, 0.0),
    softTurn: Double = 0.0,
    inputDamping: Double = 1.0)
{
    recordOdometry()

    var adjustedTranslation = translation

    adjustedTranslation = prevTranslationInput + (adjustedTranslation - prevTranslationInput) * inputDamping
    prevTranslationInput = adjustedTranslation

    if (fieldCentric) {
        adjustedTranslation = adjustedTranslation.rotateDegrees(heading.asDegrees)
    }
    adjustedTranslation += softTranslation

    var totalTurn = turn + softTurn

    if (inputDamping != 1.0)
        totalTurn = prevTurn + (totalTurn - prevTurn) * inputDamping

    prevTurn = totalTurn

    // consider only doing this if one of the sticks is out of deadband to prevent wheels going in a circle for slight turning
    if (adjustedTranslation.length > 0.01 && totalTurn.absoluteValue < 0.01) {
        if (teleopClosedLoopHeading) {  // closed loop on heading position
            // heading error
            val headingError = (headingSetpoint - heading).wrap()
            //println("Heading Error: $headingError.")

            // heading d
            val deltaHeadingError = headingError - prevHeadingError
            prevHeadingError = headingError

            totalTurn = headingError.asDegrees * parameters.kpHeading * 0.60 + deltaHeadingError.asDegrees * parameters.kdHeading
        } else if (parameters.gyroRateCorrection > 0.0) {  // closed loop on heading velocity
            totalTurn += (totalTurn * MAXHEADINGSPEED_DEGREES_PER_SECOND - headingRate.changePerSecond.asDegrees) * parameters.gyroRateCorrection
        }
    } else {
        headingSetpoint = heading
    }

    if (adjustedTranslation.x == 0.0 && adjustedTranslation.y == 0.0 && totalTurn == 0.0) {
        return stop()
    }

   // totalTurn += (totalTurn * 300.0 - headingRate.changePerSecond.asDegrees) * parameters.gyroRateCorrection //problem?

    val speeds = Array(modules.size) { 0.0 }

    for (i in 0 until modules.size) {
        speeds[i] = modules[i].calculateAngleReturnSpeed(adjustedTranslation, totalTurn, robotPivot)
    }

    val maxSpeed = speeds.maxBy(Math::abs)!!
    if (maxSpeed > 1.0) {
        for (i in 0 until speeds.size) {
            speeds[i] /= maxSpeed
        }
    }

    for (i in 0 until modules.size) {
        //print("${modules[i].currDistance} ")
        modules[i].setDrivePower(speeds[i])
    }
    //println()
}

private fun SwerveDrive.Module.calculateAngleReturnSpeed(
    translation: Vector2,
    turn: Double,
    robotPivot: Vector2
): Double {
    val localGoal = translation + (modulePosition - robotPivot).perpendicular().normalize() * turn
    var power = localGoal.length
    var setPoint = localGoal.angle.radians
    val angleError = (setPoint - angle).wrap()
    if (Math.abs(angleError.asRadians) > Math.PI / 2.0) {
        setPoint -= Math.PI.radians
        power = -power
    }
    angleSetpoint = setPoint
    return power * Math.abs(angleError.cos())
}

suspend fun SwerveDrive.Module.steerToAngle(angle: Angle, tolerance: Angle = 2.degrees) {
    try {
        periodic(watchOverrun = false) {
            angleSetpoint = angle

            val error = (angle - this@steerToAngle.angle).wrap()

            if (error.asRadians.absoluteValue < tolerance.asRadians) stop()
        }
        delay(0.2)
    } finally {
        stop()
    }
}

fun SwerveDrive.Module.recordOdometry(heading: Angle): Vector2 {
    val angleInFieldSpace = heading + angle
    val deltaDistance = currDistance - prevDistance
    prevDistance = currDistance
    return Vector2(
        deltaDistance * sin(angleInFieldSpace.asRadians),
        deltaDistance * cos(angleInFieldSpace.asRadians)
    )
}

fun SwerveDrive.recordOdometry() {
    var translation = Vector2(0.0, 0.0)

    val translations: Array<Vector2> = Array(modules.size) { Vector2(0.0, 0.0) }
    for (i in 0 until modules.size) {
        translations[i] = modules[i].recordOdometry(heading)
    }

    for (i in 0 until modules.size) {
        translation += translations[i]
    }
    translation /= modules.size.toDouble()

    position += translation
    val time = Timer.getFPGATimestamp()
    val deltaTime = time - prevTime
    velocity = (position - prevPosition) / deltaTime

    poseHistory[InterpolatingDouble(time)] = pose
    prevTime = time
    prevPosition = position
//    println("Position: $position")
}

fun SwerveDrive.resetOdometry() {
    for (module in modules) {
        module.prevDistance = 0.0
    }
    zeroEncoders()
    position = Vector2(0.0, 0.0)
    heading = ((0.0).degrees)
}

suspend fun SwerveDrive.driveAlongPath(
    path: Path2D,
    resetOdometry: Boolean = false,
    extraTime: Double = 0.0
) {
    println("Driving along path ${path.name}, duration: ${path.durationWithSpeed}, travel direction: ${path.robotDirection}, mirrored: ${path.isMirrored}")

    if (resetOdometry) {
        println("Position = $position Heading = $heading")
        resetOdometry()

        // set to the numbers required for the start of the path
        position = path.getPosition(0.0)
        heading = path.headingCurve.getValue(0.0).degrees
        if(parameters.alignRobotToPath) {
            heading += path.getTangent(0.0).angle.degrees
        }
        println("After Reset Position = $position Heading = $heading")
    }
    var prevTime = 0.0

    val timer = Timer()
    timer.start()
    prevPathPosition = path.getPosition(0.0)
    prevPathHeading = path.getAbsoluteHeadingDegreesAt(0.0).degrees
    var prevPositionError = Vector2(0.0, 0.0)
    prevHeadingError = 0.0.degrees
    periodic {
        val t = timer.get()
        val dt = t - prevTime

        // position error
        val pathPosition = path.getPosition(t)
        val positionError = pathPosition - position
        //println("time=$t   pathPosition=$pathPosition position=$position positionError=$positionError")

        // position feed forward
        val pathVelocity = (pathPosition - prevPathPosition) / dt
        prevPathPosition = pathPosition

        // position d
        val deltaPositionError = positionError - prevPositionError
        prevPositionError = positionError

        val translationControlField =
            pathVelocity * parameters.kPositionFeedForward + positionError * parameters.kpPosition + deltaPositionError * parameters.kdPosition

        // heading error
        val robotHeading = heading
        val pathHeading = path.getAbsoluteHeadingDegreesAt(t).degrees
        val headingError = (pathHeading - robotHeading).wrap()
        //println("Heading Error: $headingError. Hi. %%%%%%%%%%%%%%%%%%%%%%%%%%")

        // heading feed forward
        val headingVelocity = (pathHeading.asDegrees - prevPathHeading.asDegrees) / dt
        prevPathHeading = pathHeading

        // heading d
        val deltaHeadingError = headingError - prevHeadingError
        prevHeadingError = headingError

        val turnControl = headingVelocity * parameters.kHeadingFeedForward + headingError.asDegrees * parameters.kpHeading + deltaHeadingError.asDegrees * parameters.kdHeading

        // send it
        drive(translationControlField, turnControl, true)

        // are we done yet?
        if (t >= path.durationWithSpeed + extraTime)
            stop()

        prevTime = t

//        println("Time=$t Path Position=$pathPosition Position=$position")
//        println("DT$dt Path Velocity = $pathVelocity Velocity = $velocity")
    }

    // shut it down
    drive(Vector2(0.0, 0.0), 0.0, true)
}


suspend fun SwerveDrive.driveAlongPathWithStrafe(
    path: Path2D,
    resetOdometry: Boolean = false,
    extraTime: Double = 0.0,
    strafeAlpha: (time: Double) -> Double,
    getStrafe: () -> Double,
    earlyExit: () -> Boolean
) {
    println("Driving along path ${path.name}, duration: ${path.durationWithSpeed}, travel direction: ${path.robotDirection}, mirrored: ${path.isMirrored}")
    if (resetOdometry) {
        println("Position = $position Heading = $heading")
        resetOdometry()

        // set to the numbers required for the start of the path
        position = path.getPosition(0.0)
        heading = path.getTangent(0.0).angle.degrees + path.headingCurve.getValue(0.0).degrees
        println("After Reset Position = $position Heading = $heading")
    }
    var prevTime = 0.0

    val timer = Timer()
    timer.start()
    prevPathPosition = path.getPosition(0.0)
    prevPathHeading = path.getAbsoluteHeadingDegreesAt(0.0).degrees
    periodic {
        val t = timer.get()
        val dt = t - prevTime


        // position error
        val pathPosition = path.getPosition(t)
        val positionError = pathPosition - position
        //println("pathPosition=$pathPosition position=$position positionError=$positionError")

        // position feed forward
        val pathVelocity = (pathPosition - prevPathPosition) / dt
        prevPathPosition = pathPosition

        val translationControlField =
            pathVelocity * parameters.kPositionFeedForward + positionError * parameters.kpPosition

        // heading error
        val robotHeading = heading
        val pathHeading = path.getAbsoluteHeadingDegreesAt(t).degrees
        val headingError = (pathHeading - robotHeading).wrap()

        // heading feed forward
        val headingVelocity = (pathHeading.asDegrees - prevPathHeading.asDegrees) / dt
        prevPathHeading = pathHeading

        var turnControl =
            headingVelocity * parameters.kHeadingFeedForward + headingError.asDegrees * parameters.kpHeading

        val heading = (heading + (headingRate * parameters.gyroRateCorrection).changePerSecond).wrap()
        var translationControlRobot = translationControlField.rotateDegrees(heading.asDegrees)

        val alpha = strafeAlpha(t)
        if (alpha > 0.0) {
            translationControlRobot.x = translationControlRobot.x * (1.0 - alpha) + getStrafe() * alpha
            turnControl = 0.0
        }

        // send it
        drive(translationControlRobot, turnControl, false)

        // are we done yet?
        if (t >= path.durationWithSpeed + extraTime)
            stop()

        if (earlyExit()) {
            stop()
        }

        prevTime = t

//        println("Time=$t Path Position=$pathPosition Position=$position")
//        println("DT$dt Path Velocity = $pathVelocity Velocity = $velocity")
    }

    // shut it down
    drive(Vector2(0.0, 0.0), 0.0, true)
}

suspend fun SwerveDrive.tuneDrivePositionController(controller: org.team2471.frc.lib.input.XboxController) {
    var prevX = 0.0
    var prevY = 0.0
    var prevTime = 0.0
    var prevPositionError = Vector2(0.0, 0.0)
    var prevHeadingError = 0.0.degrees

    val timer = Timer().apply { start() }

    var angleErrorAccum = 0.0.degrees
    try {
        resetOdometry()
        periodic {
            val t = timer.get()
            val dt = t - prevTime

            val x = controller.leftThumbstickX
            val y = controller.leftThumbstickY
            val turn = 75.0*controller.rightThumbstickX

            // position error
            val pathPosition = Vector2(x, y)
            val positionError = pathPosition - position

            // position d
            val deltaPositionError = positionError - prevPositionError
            prevPositionError = positionError

            val translationControlField = positionError * parameters.kpPosition + deltaPositionError * parameters.kdPosition

            // heading error
            val robotHeading = heading.asDegrees
            val pathHeading = turn.degrees
            val headingError = (pathHeading - robotHeading.degrees).wrap()
//            println("Heading Error: $headingError. Hi.")

            // heading d
            val deltaHeadingError = headingError - prevHeadingError
            prevHeadingError = headingError

            val turnControl = headingError.asDegrees * parameters.kpHeading + deltaHeadingError.asDegrees * parameters.kdHeading

            println("Error ${headingError.asDegrees}, setpoint ${pathHeading}, current pos $robotHeading")
            drive(translationControlField, turnControl, true)

            prevTime = t
        }
    } finally {
        stop()
    }
}