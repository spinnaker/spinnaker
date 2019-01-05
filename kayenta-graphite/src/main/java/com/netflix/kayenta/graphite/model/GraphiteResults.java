/*
 * Copyright 2018 Snap Inc.
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

package com.netflix.kayenta.graphite.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Slf4j
@Data
public class GraphiteResults {
    private String target;
    private List<List<Double>> datapoints;

    @JsonIgnore
    private List<Double> adjustedPointList;

    /**
     * graphite returns datapoints as a list of tuples of value and timestamp,
     * Like: [[1, 12312312], [0, 12312322], ...]
     * we need to convert them to a list of values
     *
     * @return A list of data points the match the format of kayenta
     */
    @JsonIgnore
    private List<Double> getAdjustedPointList() {
        if (!CollectionUtils.isEmpty(adjustedPointList)) {
            return this.adjustedPointList;
        } else {
            this.adjustedPointList = new ArrayList<>();
            for (List<Double> point : this.datapoints) {
                if (point.get(0) == null) {
                    this.adjustedPointList.add(Double.NaN);
                } else {
                    this.adjustedPointList.add(point.get(0));
                }
            }
        }
        return this.adjustedPointList;
    }

    @JsonIgnore
    public Stream<Double> getDataPoints() {
        return this.getAdjustedPointList().stream();
    }

    /**
     * graphite's json render api will not explicitly show interval of datapoints.
     * as it returns a list a tuple of (value, timestamp), we need to get the diff of first two endpoints
     *
     * @return the interval of two datapoints
     */
    @JsonIgnore
    public Long getInterval() {
        if (datapoints.size() >= 2) {
            List<Double> first = datapoints.get(0);
            List<Double> second = datapoints.get(1);

            if (first.size() == 2 && second.size() == 2) {
                return (long) (second.get(1) - first.get(1));
            } else {
                throw new IllegalArgumentException(
                    "data format from graphite is invalid, expected size of 2 for datapoint, got: "
                        + " first: " + first
                        + " second: " + second);
            }
        } else {
            return 1L;
        }
    }

    @JsonIgnore
    public Long getIntervalMills() {
        return this.getInterval() * 1000;
    }

    @JsonIgnore
    public Long getStart() {
        if (!CollectionUtils.isEmpty(this.datapoints)) {
            List<Double> firstDatapoint = this.datapoints.get(0);
            if (firstDatapoint.size() == 2) {
                return firstDatapoint.get(1).longValue();
            } else {
                throw new IllegalArgumentException(
                    "data format from graphite is invalid, expected size of 2 for datapoint, got: " + firstDatapoint);
            }
        } else {
            return 0L;
        }
    }

    @JsonIgnore
    public Long getStartMills() {
        return this.getStart() * 1000;
    }

    @JsonIgnore
    public Long getEnd() {
        if (!CollectionUtils.isEmpty(this.datapoints)) {
            List<Double> lastDatapoint = this.datapoints.get(this.datapoints.size() - 1);
            if (lastDatapoint.size() == 2) {
                return lastDatapoint.get(1).longValue();
            } else {
                throw new IllegalArgumentException(
                    "data format from graphite is invalid, expected size of 2 for datapoint, got: " + lastDatapoint);
            }
        } else {
            return 0L;
        }
    }

    @JsonIgnore
    public Long getEndMills() {
        return this.getEnd() * 1000;
    }
}
