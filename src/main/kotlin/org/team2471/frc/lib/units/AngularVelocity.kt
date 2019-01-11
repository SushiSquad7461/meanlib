@file:Suppress("NOTHING_TO_INLINE")

package org.team2471.frc.lib.units

import org.team2471.frc.lib.Unproven

@Unproven
inline class AngularVelocity(val changePerSecond: Angle) {
    operator fun plus(other: AngularVelocity) = AngularVelocity(changePerSecond + other.changePerSecond)

    operator fun minus(other: AngularVelocity) = AngularVelocity(changePerSecond - other.changePerSecond)

    operator fun times(factor: Double) = AngularVelocity(changePerSecond * factor)

    operator fun div(factor: Double) = AngularVelocity(changePerSecond / factor)

    operator fun rem(other: AngularVelocity) = AngularVelocity(changePerSecond % other.changePerSecond)

    operator fun unaryPlus() = this

    operator fun unaryMinus() = AngularVelocity(-changePerSecond)

    operator fun compareTo(other: AngularVelocity) = changePerSecond.compareTo(other.changePerSecond)

    override fun toString() = "$changePerSecond per second"
}

// constructors
@Unproven
inline val Angle.perSecond get() = AngularVelocity(this)

@Unproven
inline operator fun Angle.div(time: Time) = AngularVelocity(this / time.asSeconds)

// destructors
@Unproven
inline operator fun AngularVelocity.times(time: Time) = changePerSecond / time.asSeconds