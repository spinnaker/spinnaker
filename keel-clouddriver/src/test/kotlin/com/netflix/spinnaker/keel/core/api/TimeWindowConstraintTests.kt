package com.netflix.spinnaker.keel.core.api

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import strikt.api.Assertion
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.doesNotContain
import strikt.assertions.endInclusive
import strikt.assertions.first
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.second
import strikt.assertions.start
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal class TimeWindowConstraintTests {
  val aMondayAt2am = ZonedDateTime.of(
    LocalDate.of(2021, 8, 2),
    LocalTime.of(2, 0),
    ZoneId.systemDefault()
  ).apply {
    check(dayOfWeek == MONDAY)
  }

  val aTuesdayAt2am = ZonedDateTime.of(
    LocalDate.of(2021, 8, 3),
    LocalTime.of(2, 0),
    ZoneId.systemDefault()
  ).apply {
    check(dayOfWeek == TUESDAY)
  }

  val aTuesdayAt2pm = ZonedDateTime.of(
    LocalDate.of(2021, 8, 3),
    LocalTime.of(14, 0),
    ZoneId.systemDefault()
  ).apply {
    check(dayOfWeek == TUESDAY)
  }

  val aTuesdayAt1_59pm = ZonedDateTime.of(
    LocalDate.of(2021, 8, 3),
    LocalTime.of(13, 59, 59),
    ZoneId.systemDefault()
  ).apply {
    check(dayOfWeek == TUESDAY)
  }

  val aSundayAt2pm = ZonedDateTime.of(
    LocalDate.of(2021, 8, 8),
    LocalTime.of(14, 0),
    ZoneId.systemDefault()
  ).apply {
    check(dayOfWeek == SUNDAY)
  }

  @Test
  fun `window hours include all hours in a simple window`() {
    val constraint = TimeWindowConstraint(
      windows = listOf(
        TimeWindow(
          days = "Mon-Fri",
          hours = "9-17"
        )
      )
    )

    expectThat(constraint.windowsNumeric.single().hours) {
      contains(9)
      contains(16)
      doesNotContain(17) // the range is up to 17:00, not up to 17:59
    }
  }

  @Test
  fun `window range is null if currently outside the window`() {
    val constraint = TimeWindowConstraint(
      windows = listOf(
        TimeWindow(
          days = "Mon-Fri",
          hours = "9-17"
        )
      )
    )

    expectThat(constraint.activeWindowBoundsOrNull(aSundayAt2pm)).isNull()
  }

  @Test
  fun `window range is based on time passed`() {
    val constraint = TimeWindowConstraint(
      windows = listOf(
        TimeWindow(
          days = "Mon-Fri",
          hours = "9-17"
        )
      )
    )

    expectThat(constraint.activeWindowBoundsOrNull(aTuesdayAt2pm)).isNotNull().and {
      start isEqualTo aTuesdayAt2pm.with(LocalTime.of(9, 0))
      endInclusive isEqualTo aTuesdayAt2pm.with(LocalTime.of(16, 59, 59))
    }
  }

  @Test
  fun `window range does not consider the end hour part of the window`() {
    val constraint = TimeWindowConstraint(
      windows = listOf(
        TimeWindow(
          days = "Mon-Fri",
          hours = "9-14"
        )
      )
    )

    expectThat(constraint.activeWindowBoundsOrNull(aTuesdayAt2pm)).isNull()
  }

  @Test
  fun `window range does consider one second before the end hour part of the window`() {
    val constraint = TimeWindowConstraint(
      windows = listOf(
        TimeWindow(
          days = "Mon-Fri",
          hours = "9-14"
        )
      )
    )

    expectThat(constraint.activeWindowBoundsOrNull(aTuesdayAt1_59pm)).isNotNull()
  }

  @Test
  @Disabled("Not supported yet")
  fun `window range treats multiple hour ranges on the same day independently`() {
    val constraint = TimeWindowConstraint(
      windows = listOf(
        TimeWindow(
          days = "Mon-Fri",
          hours = "9-11,13-17"
        )
      )
    )

    expectThat(constraint.activeWindowBoundsOrNull(aTuesdayAt2pm)).isNotNull().and {
      start isEqualTo aTuesdayAt2pm.with(LocalTime.of(13, 0))
      endInclusive isEqualTo aTuesdayAt2pm.with(LocalTime.of(16, 59, 59))
    }
  }

  @Test
  @Disabled("Not supported yet")
  fun `window range treats a window that spans midnight as continuing to the next day`() {
    val constraint = TimeWindowConstraint(
      windows = listOf(
        TimeWindow(
          days = "Mon",
          hours = "19-3"
        )
      )
    )

    expect {
      that(constraint.activeWindowBoundsOrNull(aMondayAt2am)).isNull()
      that(constraint.activeWindowBoundsOrNull(aTuesdayAt2am)).isNotNull()
    }
  }

  private val Assertion.Builder<Pair<ZonedDateTime, ZonedDateTime>>.startTime: Assertion.Builder<LocalTime>
    get() = first.get{toLocalTime()}

  private val Assertion.Builder<Pair<ZonedDateTime, ZonedDateTime>>.endTime: Assertion.Builder<LocalTime>
    get() = second.get{toLocalTime()}
}
