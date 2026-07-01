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

import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * Pure date-shift math for the term-rollover feature. This is the server-side Java port of the
 * DateManager client-side engine (initDatePicker.js): a fixed-day offset, a proportional "fit"
 * that re-anchors a whole timeline between a new first and last date, and an optional weekly snap
 * that keeps each item's weekday and clock time while moving it by whole weeks.
 *
 * <p>All methods operate on {@link ZonedDateTime} in the user's timezone: weekday and time-of-day
 * are timezone-sensitive (especially the snap), so callers must convert to the user's zone before
 * shifting and interpret the results in that same zone. No Sakai dependencies - keep it pure so it
 * can be unit tested in isolation.</p>
 */
public final class DateShiftCalculator {

    private DateShiftCalculator() {
    }

    /**
     * Fixed offset: move a date by a whole number of days, preserving its local time of day
     * (DST-aware, mirroring the JS shifter's moment.add(days)).
     */
    public static ZonedDateTime shiftByDays(ZonedDateTime date, long days) {
        return date.plusDays(days);
    }

    /**
     * Proportional "fit" (Smart Shift): map {@code current} from the old [oldStart, oldEnd] timeline
     * onto the new [newFirst, newLast] range. The earliest date lands exactly on newFirst, the latest
     * exactly on newLast, and everything in between is placed proportionally. When {@code snap} is true
     * the in-between result is snapped to its own weekday and clock time (whole-week spacing).
     *
     * <p>Degenerate old span (all dates identical, or a single date) collapses onto newFirst.</p>
     */
    public static ZonedDateTime fit(ZonedDateTime current,
                                    ZonedDateTime oldStart, ZonedDateTime oldEnd,
                                    ZonedDateTime newFirst, ZonedDateTime newLast,
                                    boolean snap) {
        long oldStartMs = oldStart.toInstant().toEpochMilli();
        long oldSpan = oldEnd.toInstant().toEpochMilli() - oldStartMs;
        if (oldSpan <= 0) {
            return newFirst;
        }

        double frac = (double) (current.toInstant().toEpochMilli() - oldStartMs) / (double) oldSpan;
        if (frac <= 0d) {
            return newFirst;
        }
        if (frac >= 1d) {
            return newLast;
        }

        long newFirstMs = newFirst.toInstant().toEpochMilli();
        long newSpan = newLast.toInstant().toEpochMilli() - newFirstMs;
        long targetMs = newFirstMs + Math.round(frac * (double) newSpan);
        ZonedDateTime target = ZonedDateTime.ofInstant(Instant.ofEpochMilli(targetMs), newFirst.getZone());

        return snap ? snapToSourceWeekday(target, current) : target;
    }

    /**
     * Move {@code target} to the nearest day (within +/-3 days) that shares {@code source}'s weekday,
     * carrying {@code source}'s clock time. Two items that share a weekday therefore stay a whole
     * number of weeks apart. Mirrors snapToSourceWeekday in the JS engine.
     */
    public static ZonedDateTime snapToSourceWeekday(ZonedDateTime target, ZonedDateTime source) {
        ZonedDateTime result = target
                .withHour(source.getHour())
                .withMinute(source.getMinute())
                .withSecond(source.getSecond())
                .withNano(0);

        int deltaDays = source.getDayOfWeek().getValue() - result.getDayOfWeek().getValue();
        if (deltaDays > 3) {
            deltaDays -= 7;
        } else if (deltaDays < -3) {
            deltaDays += 7;
        }
        return deltaDays == 0 ? result : result.plusDays(deltaDays);
    }
}
