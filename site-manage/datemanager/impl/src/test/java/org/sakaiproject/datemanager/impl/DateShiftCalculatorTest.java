/*
 * Copyright (c) 2003-2026 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.datemanager.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Test;

/**
 * Parity tests for {@link DateShiftCalculator} - the Java port of the DateManager JS shift math.
 * Uses a fixed UTC zone so wall-clock math is deterministic, mirroring the JS suite which runs
 * under TZ=UTC.
 */
public class DateShiftCalculatorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private static ZonedDateTime d(String isoLocal) {
        return LocalDateTime.parse(isoLocal).atZone(UTC);
    }

    // ----- shiftByDays (fixed offset) -----

    @Test
    public void shiftByDaysKeepsTimeOfDay() {
        assertEquals(d("2026-01-20T09:00:00"), DateShiftCalculator.shiftByDays(d("2026-01-06T09:00:00"), 14));
        assertEquals(d("2026-01-06T09:00:00"), DateShiftCalculator.shiftByDays(d("2026-01-20T09:00:00"), -14));
        assertEquals(d("2026-01-06T09:00:00"), DateShiftCalculator.shiftByDays(d("2026-01-06T09:00:00"), 0));
    }

    // ----- snapToSourceWeekday -----

    @Test
    public void snapLandsOnSourceWeekdayNearestTargetKeepingTime() {
        // source Monday 09:30:15, target Wednesday 2 days later -> the Monday, source's time
        ZonedDateTime source = d("2026-09-14T09:30:15");
        ZonedDateTime result = DateShiftCalculator.snapToSourceWeekday(d("2026-09-16T00:00:00"), source);
        assertEquals(DayOfWeek.MONDAY, result.getDayOfWeek());
        assertEquals(9, result.getHour());
        assertEquals(30, result.getMinute());
        assertEquals(15, result.getSecond());
        assertEquals(LocalDate.parse("2026-09-14"), result.toLocalDate());
    }

    @Test
    public void snapNeverMovesMoreThanThreeDaysAndPreservesWeekdayForAllCombos() {
        ZonedDateTime monday = d("2026-09-14T00:00:00");
        for (int t = 0; t < 7; t++) {
            ZonedDateTime target = monday.plusDays(t);
            for (int s = 0; s < 7; s++) {
                ZonedDateTime source = monday.plusDays(s).withHour(8).withMinute(5).withSecond(0);
                ZonedDateTime result = DateShiftCalculator.snapToSourceWeekday(target, source);
                assertEquals("weekday preserved (t=" + t + ", s=" + s + ")",
                        source.getDayOfWeek(), result.getDayOfWeek());
                assertEquals(8, result.getHour());
                long shiftDays = (result.toLocalDate().toEpochDay() - target.toLocalDate().toEpochDay());
                assertTrue("within +/-3 days (t=" + t + ", s=" + s + ", shift=" + shiftDays + ")",
                        shiftDays >= -3 && shiftDays <= 3);
            }
        }
    }

    @Test
    public void snapKeepsSameWeekdayItemsAWholeNumberOfWeeksApart() {
        ZonedDateTime source = d("2026-09-16T10:00:00"); // Wednesday
        ZonedDateTime r1 = DateShiftCalculator.snapToSourceWeekday(d("2026-09-14T00:00:00"), source);
        ZonedDateTime r2 = DateShiftCalculator.snapToSourceWeekday(d("2026-09-14T00:00:00").plusDays(14), source);
        long diffDays = r2.toLocalDate().toEpochDay() - r1.toLocalDate().toEpochDay();
        assertEquals(0, diffDays % 7);
    }

    // ----- fit (proportional / Smart Shift) -----

    private static final ZonedDateTime FIRST = ZonedDateTime.of(2026, 7, 1, 0, 0, 0, 0, UTC);
    private static final ZonedDateTime LAST = ZonedDateTime.of(2026, 9, 19, 0, 0, 0, 0, UTC);

    @Test
    public void fitMapsEarliestToNewFirstAndLatestToNewLast() {
        ZonedDateTime oldStart = ms(1000), oldEnd = ms(2000);
        assertEquals(FIRST, DateShiftCalculator.fit(oldStart, oldStart, oldEnd, FIRST, LAST, false));
        assertEquals(LAST, DateShiftCalculator.fit(oldEnd, oldStart, oldEnd, FIRST, LAST, false));
    }

    @Test
    public void fitPlacesMiddleProportionallyWithoutSnap() {
        long newSpan = LAST.toInstant().toEpochMilli() - FIRST.toInstant().toEpochMilli();
        ZonedDateTime result = DateShiftCalculator.fit(ms(1500), ms(1000), ms(2000), FIRST, LAST, false);
        assertEquals(FIRST.toInstant().toEpochMilli() + newSpan / 2, result.toInstant().toEpochMilli());
    }

    @Test
    public void fitDegenerateSpanCollapsesOntoNewFirst() {
        assertEquals(FIRST, DateShiftCalculator.fit(ms(5000), ms(5000), ms(5000), FIRST, LAST, false));
    }

    @Test
    public void fitSnapsMiddleToSourceWeekdayNearProportionalTarget() {
        long newSpan = LAST.toInstant().toEpochMilli() - FIRST.toInstant().toEpochMilli();
        ZonedDateTime source = d("2026-08-12T13:00:00"); // mid-term Wednesday
        ZonedDateTime result = DateShiftCalculator.fit(source, d("2026-07-15T00:00:00"), d("2026-09-05T00:00:00"),
                FIRST, LAST, true);
        assertEquals(source.getDayOfWeek(), result.getDayOfWeek());
        long proportional = FIRST.toInstant().toEpochMilli() + newSpan / 2;
        long shiftDays = Math.abs(result.toInstant().toEpochMilli() - proportional) / DAY_MS;
        assertTrue("snap stays within ~half a week of the proportional target (shift=" + shiftDays + ")",
                shiftDays <= 4);
    }

    @Test
    public void fitLatestDateWinsTheEndAnchorRegardlessOfRole() {
        // The behaviour that surprised us in manual testing: an open date later than due/accept still
        // maps to the LAST anchor because fit sorts by actual calendar date.
        assertEquals(LAST, DateShiftCalculator.fit(ms(1000), ms(100), ms(1000), FIRST, LAST, false));
    }

    private static ZonedDateTime ms(long epochMilli) {
        return java.time.Instant.ofEpochMilli(epochMilli).atZone(UTC);
    }
}
