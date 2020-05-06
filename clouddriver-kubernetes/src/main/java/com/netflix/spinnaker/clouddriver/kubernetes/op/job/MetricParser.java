/*
 * Copyright 2020 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.op.job;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Streams;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPodMetric.ContainerMetric;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@NonnullByDefault
final class MetricParser {
  private static final Splitter lineSplitter = Splitter.on('\n').trimResults().omitEmptyStrings();

  /**
   * Given the output of a kubectl top command, parses the metrics returning a MetricLine for each
   * line successfully parsed.
   *
   * <p>If the output is empty or is in an unrecognized format, returns an empty list.
   *
   * @param kubectlOutput the output from kubectl top
   * @return The parsed metrics
   */
  static ImmutableSetMultimap<String, ContainerMetric> parseMetrics(String kubectlOutput) {
    Iterator<String> lines = lineSplitter.split(kubectlOutput.trim()).iterator();
    if (!lines.hasNext()) {
      return ImmutableSetMultimap.of();
    }

    Optional<MetricParser.LineParser> optionalParser =
        MetricParser.LineParser.withHeader(lines.next());
    if (!optionalParser.isPresent()) {
      return ImmutableSetMultimap.of();
    }
    MetricParser.LineParser parser = optionalParser.get();
    return Streams.stream(lines)
        .map(parser::readLine)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(
            ImmutableSetMultimap.toImmutableSetMultimap(
                MetricParser.MetricLine::getPod, MetricParser.MetricLine::toContainerMetric));
  }

  @Slf4j
  private static final class LineParser {
    private static final Splitter columnSplitter =
        Splitter.on(Pattern.compile("\\s+")).trimResults();
    private final ImmutableList<String> headers;

    private LineParser(Iterable<String> header) throws IllegalArgumentException {
      ImmutableList<String> headers = ImmutableList.copyOf(header);
      if (headers.size() <= 2) {
        throw new IllegalArgumentException(
            String.format(
                "Unexpected metric format -- no metrics to report based on table header %s.",
                headers));
      }
      this.headers = headers;
    }

    /**
     * Returns a metric parser that parses metrics from an ASCII table with the input string as the
     * header. If the header is not in the expected format, logs a warning and returns an empty
     * optional.
     *
     * @param header header of the ASCII table
     * @return A optional containing a metric parser if the header was in the expected format; an
     *     empty optional otherwise
     */
    static Optional<LineParser> withHeader(String header) {
      try {
        return Optional.of(new LineParser(columnSplitter.split(header)));
      } catch (IllegalArgumentException e) {
        log.warn(e.getMessage());
        return Optional.empty();
      }
    }

    private Optional<MetricLine> readLine(String line) {
      List<String> entry = columnSplitter.splitToList(line);
      if (entry.size() != headers.size()) {
        log.warn("Entry {} does not match column width of {}, skipping", entry, headers);
        return Optional.empty();
      }
      String podName = entry.get(0);
      String containerName = entry.get(1);
      ImmutableMap.Builder<String, String> metrics = ImmutableMap.builder();
      for (int j = 2; j < headers.size(); j++) {
        metrics.put(headers.get(j), entry.get(j));
      }
      return Optional.of(new MetricLine(podName, containerName, metrics.build()));
    }
  }

  private static final class MetricLine {
    @Getter private final String pod;
    private final String container;
    private final ImmutableMap<String, String> metrics;

    private MetricLine(String pod, String container, Map<String, String> metrics) {
      this.pod = pod;
      this.container = container;
      this.metrics = ImmutableMap.copyOf(metrics);
    }

    ContainerMetric toContainerMetric() {
      return new ContainerMetric(container, metrics);
    }
  }
}
