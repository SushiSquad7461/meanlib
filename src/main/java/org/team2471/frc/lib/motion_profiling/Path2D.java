package org.team2471.frc.lib.motion_profiling;

import org.team2471.frc.lib.vector.Vector2;

public class Path2D {

  private MotionCurve m_yxCurve;  // positive y is forward in robot space, and positive x is to the robot's right
  private MotionCurve m_easeCurve;  // the ease curve is the percentage along the path the robot as a function of time
  private double m_robotWidth = 24.0 / 12.0;  // average FRC robots are 28 inches wide, converted to feet.
  private Vector2 m_prevCenterPosition;
  private Vector2 m_prevLeftPosition;
  private Vector2 m_prevRightPosition;

  public Path2D() {
    m_yxCurve = new MotionCurve();
    m_easeCurve = new MotionCurve();
  }

  public void reset() {
    m_prevCenterPosition = null;
    m_prevLeftPosition = null;
    m_prevRightPosition = null;
  }

  public void addVector2( Vector2 point ) {
    addPoint( point.x, point.y );
  }

  public void addPoint( double x, double y ) {
    m_yxCurve.storeValue( y, x );  // we store y then x so that 0 slope key generates a dx/dy = 0, which is how we want to start
  }

  public void addEasePoint( double time, double value ) {
    m_easeCurve.storeValue( time, value );
  }

  public Vector2 getPosition( double time ) {
    return new Vector2( m_xCurve.getValue(time), m_yCurve.getValue(time) );
  }

  public Vector2 getTangent( double time ) {
    return new Vector2( m_xCurve.getDerivative(time), m_yCurve.getDerivative(time));
  }

  public Vector2 getSidePosition( double time, double xOffset ) {  // offset can be positive or negative (half the width of the robot)
    Vector2 centerPosition = getPosition( time );  // this could compute the position for a specific offset vector on the robot
    Vector2 tangent = getTangent( time );
    tangent = Vector2.normalize( tangent );
    tangent = Vector2.perpendicular( tangent );
    tangent = Vector2.multiply( tangent, xOffset );
    Vector2 sidePosition = Vector2.add( centerPosition, tangent );
    return sidePosition;
  }

  public Vector2 getLeftPosition( double time ) {
    return getSidePosition( time, -m_robotWidth / 2.0 );
  }

  public Vector2 getRightPosition( double time ) {
    return getSidePosition( time, m_robotWidth / 2.0 );
  }

  public double getLeftPositionDelta( double time ) {
    if (m_prevLeftPosition == null) {
      m_prevCenterPosition = getPosition( time );
      m_prevLeftPosition = getLeftPosition( time );
      return 0.0;
    }

    Vector2 centerPosition = getPosition(time);
    Vector2 leftPosition = getLeftPosition(time);
    Vector2 deltaCenter = Vector2.subtract( getPosition(time), m_prevCenterPosition );
    Vector2 deltaLeft = Vector2.subtract( getLeftPosition(time), m_prevLeftPosition );
    m_prevCenterPosition = centerPosition;
    m_prevLeftPosition = leftPosition;

    if (Vector2.dot(deltaCenter, deltaLeft) > 0) {
      return Vector2.length(deltaLeft);
    }
    else {
      return -Vector2.length(deltaLeft);
    }
  }

  public double getRightPositionDelta( double time ) {
    if (m_prevRightPosition == null) {
      m_prevCenterPosition = getPosition( time );
      m_prevRightPosition = getRightPosition( time );
      return 0.0;
    }

    Vector2 centerPosition = getPosition(time);
    Vector2 RightPosition = getRightPosition(time);
    Vector2 deltaCenter = Vector2.subtract( getPosition(time), m_prevCenterPosition );
    Vector2 deltaRight = Vector2.subtract( getRightPosition(time), m_prevRightPosition );
    m_prevCenterPosition = centerPosition;
    m_prevRightPosition = RightPosition;

    if (Vector2.dot(deltaCenter, deltaRight) > 0) {
      return Vector2.length(deltaRight);
    }
    else {
      return -Vector2.length(deltaRight);
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
