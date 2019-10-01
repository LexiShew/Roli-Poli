package org.firstinspires.ftc.teamcode.components;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.Range;

public class DriveSystem {
    private DcMotor motorFrontLeft;
    private DcMotor motorFrontRight;
    private DcMotor motorBackLeft;
    private DcMotor motorBackRight;
    public DcMotor[] motors = new DcMotor[4];

    public IMUSystem imuSystem;
    protected HardwareMap hardwareMap;

    private final int TICKS_IN_INCH = 69;

    /**
     * Handles the data for the abstract creation of a drive system with four wheels
     */
    public DriveSystem(HardwareMap hardwareMap) {

        this.hardwareMap = hardwareMap;
        initMotors();
        imuSystem = new IMUSystem(hardwareMap);
    }

    /**
     * Set the power of the drive system
     * @param power power of the system
     */
    public void setMotorPower(double power) {
        for (DcMotor motor : motors) {
            motor.setPower(power);
        }
    }

    public void initMotors() {

        this.motorFrontLeft = hardwareMap.dcMotor.get("motorFL");
        this.motorFrontRight = hardwareMap.dcMotor.get("motorFR");
        this.motorBackRight = hardwareMap.dcMotor.get("motorBR");
        this.motorBackLeft = hardwareMap.dcMotor.get("motorBL");

        motors[0] = motorFrontLeft;
        motors[1] = motorFrontRight;
        motors[2] = motorBackRight;
        motors[3] = motorBackLeft;

        for (DcMotor motor : motors) {
            motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            motor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        }

        motorFrontRight.setDirection(DcMotorSimple.Direction.REVERSE);
        motorFrontLeft.setDirection(DcMotorSimple.Direction.FORWARD);
        motorBackRight.setDirection(DcMotorSimple.Direction.REVERSE);
        motorBackLeft.setDirection(DcMotorSimple.Direction.FORWARD);

        setMotorPower(0);
    }

    /**
     * Clips joystick values and drives the motors.
     * @param rightX Right X joystick value
     * @param rightY Right Y joystick value
     * @param leftX Left X joystick value
     * @param leftY Left Y joystick value in case you couldn't tell from the others
     * @param slowDrive Set to true for 30 % motor power.
     */
    public void drive(float rightX, float rightY, float leftX, float leftY, boolean slowDrive) {
        // Prevent small values from causing the robot to drift
        if (Math.abs(rightX) < 0.01) {
            rightX = 0.0f;
        }
        if (Math.abs(leftX) < 0.01) {
            leftX = 0.0f;
        }
        if (Math.abs(leftY) < 0.01) {
            leftY = 0.0f;
        }
        if (Math.abs(rightY) < 0.01) {
            rightY = 0.0f;
        }

        // write the values to the motors 1
        double frontLeftPower = -leftY - leftX + rightX;
        double frontRightPower = -leftY + leftX - rightX;
        double backLeftPower = -leftY - leftX - rightX;
        double backRightPower = -leftY + leftX + rightX;

        this.motorFrontRight.setPower(Range.clip(frontRightPower, -1, 1));
        this.motorBackRight.setPower(Range.clip(backRightPower, -1, 1));
        this.motorFrontLeft.setPower(Range.clip(frontLeftPower, -1, 1));
        this.motorBackLeft.setPower(Range.clip(backLeftPower, -1, 1));
    }

    private void driveToPositionTicks(int ticks, Direction direction, double maxPower) {
        int dir = direction.ordinal();
        for (int i = 0; i < motors.length; i++) {
            DcMotor motor = motors[i];
            motor.setTargetPosition(motor.getCurrentPosition() +
                    (int)(Math.pow(-1, (i == 1 || i == 2) ? Integer.bitCount(dir) : dir)) * ticks);
        }

        setRunMode(DcMotor.RunMode.RUN_TO_POSITION);
        setMotorPower(maxPower);
        double heading = -imuSystem.getHeading();

        setMotorPower(0.0);
        setRunMode(DcMotor.RunMode.RUN_USING_ENCODER);
        turn(heading + imuSystem.getHeading(), 0.5);
    }

    public void setRunMode(DcMotor.RunMode runMode) {
        for (DcMotor motor : motors) {
            motor.setMode(runMode);
        }
    }

    /**
     * Gets the minimum distance from the target
     * @return
     */
    public int  getMinDistanceFromTarget() {
        int distance = Integer.MAX_VALUE;
        for (DcMotor motor : motors) {
            distance = Math.min(distance, motor.getTargetPosition() - motor.getCurrentPosition());
        }
        return distance;
    }

    public void driveToPositionInches(double inches, Direction direction, double maxPower) {
        driveToPositionTicks(inchesToTicks(inches), direction, maxPower);
    }

    /**
     * Converts inches to ticks
     * @param inches Inches to convert to ticks
     * @return
     */
    public int inchesToTicks(double inches) {
        return (int) inches * TICKS_IN_INCH;
    }

    /**
     * Turns relative the heading upon construction
     * @param degrees The degrees to turn the robot by
     * @param maxPower The maximum power of the motors
     */
    public void turnAbsolute(double degrees, double maxPower) {
        turn(degrees, maxPower);
    }

    /**
     * Turns the robot by a given amount of degrees
     * @param degrees The degrees to turn the robot by
     * @param maxPower The maximum power of the motors
     */
    public void turn(double degrees, double maxPower) {
        double targetHeading = degrees + -imuSystem.getHeading();
        targetHeading = targetHeading % 360;

        if (targetHeading < 0) {
            targetHeading = targetHeading + 360;
        }

        double heading = -imuSystem.getHeading();
        double difference = computeDegreesDiff(targetHeading, heading);
        while (Math.abs(difference) > 1.0) {
            difference = computeDegreesDiff(targetHeading, heading);
            double power = getTurnPower(difference, maxPower);
            tankDrive(-power * Math.signum(difference), power * Math.signum(difference));
            heading = -imuSystem.getHeading();
        }
        this.setMotorPower(0);
    }

    /**
     * Causes the system to tank drive
     * @param leftPower sets the left side power of the robot
     * @param rightPower sets the right side power of the robot
     */
    public void tankDrive(double leftPower, double rightPower) {
        this.motorFrontLeft.setPower(leftPower);
        this.motorBackLeft.setPower(leftPower);
        this.motorFrontRight.setPower(rightPower);
        this.motorBackRight.setPower(rightPower);
    }

    /**
     * Gets the turn power needed
     * @param degrees Number of degrees to turn
     * @return motor power from 0 - 0.8
     */
    private double getTurnPower(double degrees, double maxPower) {
        double power = Math.abs(degrees / 100.0);
        return Range.clip(power + 0.065, 0.0, maxPower);
    }

    private double computeDegreesDiff(double targetHeading, double heading) {
        double diff = targetHeading - heading;
        if (diff > 180) {
            return diff - 360;
        }
        if (diff < -180) {
            return 360 + diff;
        }
        return diff;
    }

}
