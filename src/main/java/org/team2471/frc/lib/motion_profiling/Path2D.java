package org.team2471.frc.lib.motion_profiling;

import org.team2471.frc.lib.vector.Vector2;

public class Path2D {

  private MotionCurve m_xCurve;
  private MotionCurve m_yCurve;
  private double m_robotWidth = 28.0 / 12.0;  // average FRC robots are 28 inches wide, converted to feet.
  private Vector2 m_prevCenterPosition;
  private Vector2 m_prevLeftPosition;
  private Vector2 m_prevRightPosition;

  public Path2D() {
    m_xCurve = new MotionCurve();
    m_yCurve = new MotionCurve();
  }

  public void AddVector2( double time, Vector2 point ) {
    AddPoint( time, point.x, point.y );
  }

  public void AddPoint( double time, double x, double y ) {
    m_xCurve.storeValue( time, x );
    m_yCurve.storeValue( time, y );
  }

  public Vector2 getPosition( double time ) {
    return new Vector2( m_xCurve.getValue(time), m_yCurve.getValue(time));
  }

  public Vector2 getTangent( double time ) {
    return new Vector2( m_xCurve.getDerivative(time), m_yCurve.getDerivative(time));
  }

  public Vector2 getSidePosition( double time, double xOffset ) {  // offset can be positive or negative (half the width of the robot)
    m_prevCenterPosition = getPosition( time );  // this could compute the position for a specific offset vector on the robot
    Vector2 tangent = getTangent( time );
    tangent = Vector2.normalize( tangent );
    tangent = Vector2.perpendicular( tangent );
    tangent = Vector2.multiply( tangent, xOffset );
    Vector2 sidePosition = Vector2.add( m_prevCenterPosition, tangent );
    return sidePosition;
  }

  public Vector2 getLeftPosition( double time ) {
    m_prevLeftPosition = getSidePosition( time, -m_robotWidth / 2.0 );
    return m_prevLeftPosition;
  }

  public Vector2 getRightPosition( double time ) {
    m_prevRightPosition =  getSidePosition( time, m_robotWidth / 2.0 );
    return m_prevRightPosition;
  }

  public double getLeftPositionDelta( double time ) {
    if (m_prevLeftPosition == null) {
      getLeftPosition( time );
      return 0.0;
    }
    Vector2 deltaCenter = Vector2.subtract( getPosition(time), m_prevCenterPosition );
    Vector2 deltaLeft = Vector2.subtract( getLeftPosition(time), m_prevLeftPosition );
    if (Vector2.dot(deltaCenter, deltaLeft) < 0) {
      return -Vector2.length(deltaLeft);
    }
    else {
      return Vector2.length(deltaLeft);
    }
  }

  public double getRightPositionDelta( double time ) {
    if (m_prevRightPosition == null) {
      getRightPosition( time );
      return 0.0;
    }
    Vector2 deltaCenter = Vector2.subtract( getPosition(time), m_prevCenterPosition );
    Vector2 deltaRight = Vector2.subtract( getLeftPosition(time), m_prevRightPosition );
    if (Vector2.dot(deltaCenter, deltaRight) < 0) {
      return -Vector2.length(deltaRight);
    }
    else {
      return Vector2.length(deltaRight);
    }
  }

  public double getRobotWidth() {
    return m_robotWidth;
  }

  public void setRobotWidth(double robotWidth) {
    m_robotWidth = robotWidth;
  }

  public double getMaxTime() {
    return Math.max( m_xCurve.getLength(), m_yCurve.getLength());
  }
}
