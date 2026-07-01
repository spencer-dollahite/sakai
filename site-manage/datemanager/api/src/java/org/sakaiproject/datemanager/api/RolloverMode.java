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
package org.sakaiproject.datemanager.api;

/**
 * How a site's dates should be shifted when the site is rolled over into a new term (e.g. on copy).
 *
 * <ul>
 *   <li>{@link #NONE} - do nothing (default; the whole feature is opt-in).</li>
 *   <li>{@link #OFFSET} - move every date by a fixed number of days.</li>
 *   <li>{@link #ANCHOR} - re-anchor the whole timeline proportionally between the new term's first
 *       and last date (earliest date -&gt; new first, latest -&gt; new last).</li>
 *   <li>{@link #ANCHOR_SNAP} - like {@link #ANCHOR} but each in-between item keeps its own weekday and
 *       clock time, moving by whole weeks.</li>
 * </ul>
 */
public enum RolloverMode {

    NONE,
    OFFSET,
    ANCHOR,
    ANCHOR_SNAP;

    /**
     * Lenient parse for the value stored as a site property. Accepts hyphen or underscore forms
     * (e.g. "anchor-snap"), any case; unknown/blank values map to {@link #NONE}.
     */
    public static RolloverMode fromString(String value) {
        if (value == null) {
            return NONE;
        }
        try {
            return RolloverMode.valueOf(value.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
