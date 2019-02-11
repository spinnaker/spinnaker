/*
 * Copyright 2019 Intuit, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.kayenta.wavefront.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class WavefrontTimeSeriesTest {

    @Test
    public void testGetAdjustedPointList_AddPoints() {
        List<Double> adjustedDataPoints = generateAdjustedPointList(2L);

        assertThat(adjustedDataPoints.get(0), is(0.0));
        assertThat(adjustedDataPoints.get(1), is(2.0));
        assertThat(adjustedDataPoints.get(2), is(4.0));
    }

    @Test
    public void testGetAdjustedPointList_addNaNForMissingPoints() {
        List<Double> adjustedDataPoints = generateAdjustedPointList(1L);

        assertThat(adjustedDataPoints.get(0), is(0.0));
        assertThat(adjustedDataPoints.get(1), is(Double.NaN));
        assertThat(adjustedDataPoints.get(2), is(2.0));
        assertThat(adjustedDataPoints.get(3), is(Double.NaN));
        assertThat(adjustedDataPoints.get(4), is(4.0));
    }

    @Test
    public void testGetAdjustedPointList_SkipPointsOutsideStep() {
        List<Double> adjustedDataPoints = generateAdjustedPointList(4L);

        assertThat(adjustedDataPoints.get(0), is(0.0));
        assertThat(adjustedDataPoints.get(1), is(4.0));
    }

    private List<Double> generateAdjustedPointList(long step) {
        List<List<Number>> points = new ArrayList<>();
        for (double i = 0; i < 3; i++) {
            List<Number> point = new ArrayList<>();
            point.add(2.0 * i);
            point.add(2.0 * i);
            points.add(point);
        }
        WavefrontTimeSeries.WavefrontSeriesEntry entry = new WavefrontTimeSeries.WavefrontSeriesEntry();
        entry.setData(points);
        return entry.getDataPoints(step).collect(Collectors.toList());
    }
}
