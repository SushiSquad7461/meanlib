package org.team2471.frc.lib.control.experimental.next

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.*
import edu.wpi.first.wpilibj.hal.FRCNetComm
import edu.wpi.first.wpilibj.hal.HAL
import edu.wpi.first.wpilibj.internal.HardwareHLUsageReporting
import edu.wpi.first.wpilibj.internal.HardwareTimer
import edu.wpi.first.wpilibj.util.WPILibVersion
import kotlinx.coroutines.experimental.launch
import java.io.File

interface RobotProgram {
    suspend fun autonomous()

    suspend fun teleop()

    suspend fun disable()

    suspend fun test()
}

private enum class RobotMode {
    DISABLED,
    AUTONOMOUS,
    TELEOP,
    TEST,
}

fun initializeWpilib() {
    // set up network tables
    val ntInstance = NetworkTableInstance.getDefault()
    ntInstance.setNetworkIdentity("Robot")
    ntInstance.startServer("/home/lvuser/networktables.ini")

    // initialize hardware configuration
    check(HAL.initialize(500, 0)) { "Failed to initialize. Terminating." }

    // Set some implementations so that the static methods work properly
    Timer.SetImplementation(HardwareTimer())
    HLUsageReporting.SetImplementation(HardwareHLUsageReporting())
    RobotState.SetImplementation(DriverStation.getInstance())

    // Report our robot's language as Java
    HAL.report(FRCNetComm.tResourceType.kResourceType_Language, FRCNetComm.tInstances.kLanguage_Java)

    // wpilib's RobotBase does this for some reason
    File("/tmp/frc_versions/FRC_Lib_Version.ini").writeText("Java ${WPILibVersion.Version}")

    println("wpilib initialized successfully.")
}

fun runRobotProgram(robotProgram: RobotProgram): Nothing {
    println("********** Robot program starting **********")

    val ds = DriverStation.getInstance()

    var previousRobotMode: RobotMode? = null
    val baseResource = Resource("Base")

    while (true) {
        ds.waitForData()

        if (ds.isDisabled) {
            if (previousRobotMode != RobotMode.DISABLED) {
                HAL.observeUserProgramDisabled()
                previousRobotMode = RobotMode.DISABLED

                launch(MeanlibContext) {
                    use(baseResource) { robotProgram.disable() }
                }
            }
        } else if (previousRobotMode != RobotMode.AUTONOMOUS && ds.isAutonomous) {
            HAL.observeUserProgramAutonomous()
            previousRobotMode = RobotMode.AUTONOMOUS

            launch(MeanlibContext) {
                use(baseResource) { robotProgram.autonomous() }
            }
        } else if (previousRobotMode != RobotMode.TELEOP && ds.isOperatorControl) {
            HAL.observeUserProgramTeleop()
            previousRobotMode = RobotMode.TELEOP

            launch(MeanlibContext) {
                use(baseResource) { robotProgram.teleop() }
            }
        } else if (previousRobotMode != RobotMode.TEST) {
            HAL.observeUserProgramTest()
            previousRobotMode = RobotMode.TEST

            launch(MeanlibContext) {
                use(baseResource) { robotProgram.test() }
            }
        }
    }
}
