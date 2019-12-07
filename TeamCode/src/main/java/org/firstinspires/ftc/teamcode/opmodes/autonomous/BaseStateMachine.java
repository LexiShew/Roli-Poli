package org.firstinspires.ftc.teamcode.opmodes.autonomous;

import android.util.Log;
import com.qualcomm.robotcore.util.ElapsedTime;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.tfod.Recognition;
import org.firstinspires.ftc.teamcode.components.DriveSystem;
import java.util.List;

public abstract class BaseStateMachine extends BaseAutonomous {
    public enum State {
        STATE_INITIAL,
        STATE_FIND_SKYSTONE,
        STATE_ALIGN_SKYSTONE,
        STATE_HORIZONTAL_ALIGN_SKYSTONE,
        STATE_INTAKE_SKYSTONE,
        STATE_ALIGN_STONE,
        STATE_HORIZONTAL_ALIGN_STONE,
        STATE_INTAKE_STONE,
        STATE_ALIGN_BRIDGE,
        STATE_MOVE_PAST_LINE,
        STATE_TURN_FOR_FOUNDATION,
        STATE_BACKUP_INTO_FOUNDATION,
        STATE_INITIAL_ALIGN_STONE,
        STATE_STRAFE_AWAY_FROM_FOUNDATION,
        STATE_MOVE_INTO_WALL,
        STATE_ALIGN_FOR_BRIDGE,
        STATE_FIND_STONE,
        STATE_APPROACH_STONE,
        STATE_COMPLETE,
        STATE_DEPOSIT_STONE,
        STATE_BACKUP_TO_LINE,
        STATE_TURN_FOR_BACKUP,
        STATE_BACKUP_FOR_SECOND_STONE,
        STATE_MOVE_PAST_COLOR_LINE,
        LOGGING,
        STATE_REALIGN_HEADING
    }

    private final static String TAG = "BaseStateMachine";
    private State mCurrentState;                         // Current State Machine State.
    private ElapsedTime mStateTime = new ElapsedTime();  // Time into current state

    public void init(Team team) {
        super.init(team);
        this.msStuckDetectInit = 15000;
        this.msStuckDetectInitLoop = 15000;
        newState(State.STATE_INITIAL);
    }

    private int skystoneOffset;
    private static final int DEAD_RECKON_SKYSTONE = -80;
    private double alignStone;
    @Override
    public void loop() {
        telemetry.addData("State", mCurrentState);
        telemetry.update();
        switch (mCurrentState) {
            case LOGGING:
                // telemetry.addData("DistanceFront", distanceCenter.getDistance(DistanceUnit.MM));
                telemetry.addData("Color Blue", colorSensor.blue());
                telemetry.addData("Color Red", colorSensor.red());
                telemetry.addData("Color Green", colorSensor.green());
                telemetry.addData("Color Alpha", colorSensor.alpha());
                telemetry.addData("Color Hue", colorSensor.argb());
                telemetry.update();
                break;
            case STATE_INITIAL:
                // Initialize
                // Drive 0.5m (1 tile) to the left
                newState(State.STATE_FIND_SKYSTONE);
                break;

            case STATE_FIND_SKYSTONE:
                List<Recognition> recognitions = tensorflow.getInference();
                if (recognitions != null) {
                    for (Recognition recognition : recognitions) {
                        if (recognition.getLabel().equals("Skystone")) {
                            double degrees = recognition.estimateAngleToObject(AngleUnit.DEGREES);
                            int sign = (int) Math.signum(degrees);
                            int currOffset = sign * (int) (300 * (Math.sin(Math.abs(degrees * Math.PI / 180))));
                            currOffset -= 330;
                            // The skystone detected is one of the first three which means that
                            // the second skystone must be farthest from the audience
                            if (currOffset > -380) {
                                skystoneOffset = currOffset;
                            }
                            Log.d(TAG, "Skystone offset: " + skystoneOffset);
                        }
                    }
                    newState(State.STATE_ALIGN_SKYSTONE);
                } else {
                    skystoneOffset = DEAD_RECKON_SKYSTONE;
                    newState(State.STATE_ALIGN_SKYSTONE);
                }
                break;

            case STATE_ALIGN_SKYSTONE:
                // Align to prepare intake
                if (skystoneOffset == 0) {
                    skystoneOffset = DEAD_RECKON_SKYSTONE;
                }
                if (driveSystem.driveToPosition(skystoneOffset, DriveSystem.Direction.FORWARD, 0.75)) {
                    newState(State.STATE_HORIZONTAL_ALIGN_SKYSTONE);
                }
                break;

            case STATE_HORIZONTAL_ALIGN_SKYSTONE:
                if (driveSystem.driveToPosition(1000, centerDirection, 0.7)) {
                    newState(State.STATE_INTAKE_SKYSTONE);
                }
                break;

            case STATE_INTAKE_SKYSTONE:
                if (driveSystem.driveToPosition(150, DriveSystem.Direction.FORWARD, 0.2)) {
                    intakeSystem.stop();
                    newState(State.STATE_ALIGN_BRIDGE);
                } else {
                    intakeSystem.suck();
                }
                break;

            case STATE_ALIGN_BRIDGE:
                if (driveSystem.driveToPosition(625, outsideDirection, 1.0)) {
                    newState(State.STATE_REALIGN_HEADING);
                }
                break;

            case STATE_REALIGN_HEADING:
                if (driveSystem.turnAbsolute(0, 1.0)) {
                    newState(State.STATE_MOVE_PAST_LINE);
                }
                break;

            case STATE_MOVE_PAST_LINE:
                if (driveSystem.driveToPosition(1600 - skystoneOffset, DriveSystem.Direction.FORWARD, 1.0)) {
                    newState(State.STATE_TURN_FOR_FOUNDATION);
                }
                break;

            case STATE_TURN_FOR_FOUNDATION:
                int sign = currentTeam == Team.RED ? 1 : -1;
                if (driveSystem.turnAbsolute(90 * sign, 1.0)) {
                    newState(State.STATE_BACKUP_INTO_FOUNDATION);
                }
                break;

            case STATE_BACKUP_INTO_FOUNDATION:
                if (driveSystem.driveToPosition(350, DriveSystem.Direction.BACKWARD, 1.0)) {
                    latchSystem.latch();
                    newState(State.STATE_MOVE_INTO_WALL);
                }
                break;

            case STATE_MOVE_INTO_WALL:
                if (driveSystem.driveToPosition(650, DriveSystem.Direction.FORWARD, 1.0)) {
                    latchSystem.unlatch();
                    newState(State.STATE_STRAFE_AWAY_FROM_FOUNDATION);
                }
                break;

            case STATE_STRAFE_AWAY_FROM_FOUNDATION:
                if (driveSystem.driveToPosition(500, outsideDirection, 1.0)) {
                    newState(State.STATE_TURN_FOR_BACKUP);
                }
                break;

            case STATE_TURN_FOR_BACKUP:
                if (driveSystem.turnAbsolute(0, 1.0)) {
                    newState(State.STATE_BACKUP_FOR_SECOND_STONE);
                    // Make it move more when it backs up
                    if (skystoneOffset == DEAD_RECKON_SKYSTONE) {
                        skystoneOffset = 230;
                    }
                }
                break;

            case STATE_BACKUP_FOR_SECOND_STONE:
                if (driveSystem.driveToPosition(900 + Math.abs(skystoneOffset), DriveSystem.Direction.BACKWARD, 1.0)) {
                    newState(State.STATE_FIND_STONE);
                }
                break;

            case STATE_FIND_STONE:
                recognitions = tensorflow.getInference();
                if (recognitions != null) {
                    for (Recognition recognition : recognitions) {
                        if (recognition.getLabel().equals("Stone") || recognition.getLabel().equals("Skystone")) {
                            double degrees = recognition.estimateAngleToObject(AngleUnit.DEGREES);
                            sign = (int) Math.signum(degrees);
                            alignStone = sign * (int) (300 * (Math.sin(Math.abs(degrees * Math.PI / 180))));
                            newState(State.STATE_INITIAL_ALIGN_STONE);
                            break;
                        }
                    }
                }
                break;

            case STATE_INITIAL_ALIGN_STONE:
                if (driveSystem.driveToPosition((int) alignStone - 20, DriveSystem.Direction.FORWARD, 0.75)) {
                    newState(State.STATE_APPROACH_STONE);
                }
                break;

            case STATE_APPROACH_STONE:
                if (distanceCenter.getDistance(DistanceUnit.MM) < 350) {
                    driveSystem.stopAndReset();
                    alignStone = distanceCenter.getDistance(DistanceUnit.MM);
                    newState(State.STATE_ALIGN_STONE);
                } else {
                    driveSystem.driveToPosition(750, centerDirection, 0.7);
                }
                break;

            case STATE_ALIGN_STONE:
                if (driveSystem.driveToPosition(250, DriveSystem.Direction.BACKWARD, 1.0)) {
                    newState(State.STATE_HORIZONTAL_ALIGN_STONE);
                }
                break;
            case STATE_HORIZONTAL_ALIGN_STONE:
                if (driveSystem.driveToPosition((int) alignStone + 120, centerDirection, 1.0)) {
                    newState(State.STATE_INTAKE_STONE);
                }
                break;

            case STATE_INTAKE_STONE:
                if (driveSystem.driveToPosition(225, DriveSystem.Direction.FORWARD, 1.0)) {
                    newState(State.STATE_ALIGN_FOR_BRIDGE);
                }
                break;

            case STATE_ALIGN_FOR_BRIDGE:
                if (driveSystem.driveToPosition((int) alignStone + 250, outsideDirection, 1.0)) {
                    newState(State.STATE_MOVE_PAST_COLOR_LINE);
                }
                break;

            case STATE_MOVE_PAST_COLOR_LINE:
                if (currentTeam == Team.RED) {
                    if (colorSensor.red() > colorSensor.blue() * 1.25) {
                        driveSystem.drive(0, 0, 0.0f, false);
                        newState(State.STATE_DEPOSIT_STONE);
                        break;
                    }
                } else {
                    if (colorSensor.blue() > colorSensor.red() * 1.25) {
                        driveSystem.drive(0, 0, 0.0f, false);
                        newState(State.STATE_DEPOSIT_STONE);
                        break;
                    }
                }
                Log.d(TAG, "Blue: " + colorSensor.blue() + " Red: " + colorSensor.red());
                driveSystem.drive(0, 0, -0.75f, false);
                break;


            case STATE_DEPOSIT_STONE:
                if (mStateTime.milliseconds() > 1250) {
                    intakeSystem.stop();
                    newState(State.STATE_BACKUP_TO_LINE);
                } else {
                    intakeSystem.unsuck();
                }
                break;

            case STATE_BACKUP_TO_LINE:
                if (driveSystem.driveToPosition(150, DriveSystem.Direction.BACKWARD, 1.0)) {
                    newState(State.STATE_COMPLETE);
                }
                break;

            case STATE_COMPLETE:

                break;
        }
    }

    private void newState(State newState) {
        // Restarts the state clock as well as the state
        mStateTime.reset();
        mCurrentState = newState;
    }

}