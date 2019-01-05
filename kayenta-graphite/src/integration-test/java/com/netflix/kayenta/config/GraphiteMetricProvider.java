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

package com.netflix.kayenta.config;

import java.time.Instant;
import java.util.Random;

class GraphiteMetricProvider {
    private int min;
    private int max;
    private String metricName;

    GraphiteMetricProvider(int min, int max, String metricName) {
        this.min = min;
        this.max = max;
        this.metricName = metricName;
    }

    String getRandomMetricWithinRange() {
        return String.format("%s %d %d%n", metricName,
            new Random().nextInt((max - min) + 1) + min,
            Instant.now().getEpochSecond());
    }
}

