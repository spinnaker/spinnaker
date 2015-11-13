/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.oort.bench

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

import java.util.concurrent.TimeUnit

@Component
class MetricsLogger {
  private static final Logger LOG = LoggerFactory.getLogger(MetricsLogger)

  public void log(List<EndpointMetrics> metrics) {
    def messages = [metricHeader()]
    for (metric in metrics) {
      messages << metricRow(metric)
    }
    messages << "\n\n"

    LOG.info(messages.join('\n\t\t'))
  }

  String pad(int chars, Object o) {
    String val = o as String
    if (val.length() >= chars) {
      return val.substring(0, chars)
    }
    int toAdd = chars - val.length();
    StringBuilder b = new StringBuilder()
    toAdd.times { b.append(' ')}
    b.append(val)
    b.toString()
  }

  String collectTimings(EndpointMetrics metric) {
    StringBuilder result = new StringBuilder()
    for (timing in metric.timings) {
      result.append(timing.key).append(":").append(TimeUnit.NANOSECONDS.toMillis(timing.value - metric.startTime)).append(',')
    }
    if (result.length() > 0) {
      result.setLength(result.length() - 1);
    }
    result.toString()
  }

  String metricHeader() {
    "\n\n\t\t${pad(18, 'Host')}|${pad(28, 'Path')}|${pad(10, 'HTTP Code')}|${pad(18, 'Total Bytes')}|${pad(12, 'Timings')}"
  }

  String metricRow(EndpointMetrics metric) {
    String start = "${pad(18, metric.uri.host)}|${pad(28,metric.uri.path)}|"
    if (metric.exception) {
      return "${start}Failed: $metric.exception.message"
    }
    return "${start}${pad(10, metric.httpResponseCode)}|${pad(18, metric.totalBytes)}|${collectTimings(metric)}"
  }
}
