/*
 * Copyright 2019 Playtika
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

package com.netflix.kayenta.metrics;

import static java.util.Arrays.asList;

import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;

/**
 * Retry configuration for metrics fetching from metrics storage
 *
 * @author Anastasiia Smirnova
 */
@Data
@ConfigurationProperties("kayenta.metrics.retry")
public class MetricsRetryConfigurationProperties {

  // TODO: with java 11 replace with Set.of
  private Set<HttpStatus.Series> series = new HashSet<>(asList(HttpStatus.Series.SERVER_ERROR));

  private Set<HttpStatus> statuses =
      new HashSet<>(asList(HttpStatus.REQUEST_TIMEOUT, HttpStatus.TOO_MANY_REQUESTS));

  private int attempts = 10;
}
