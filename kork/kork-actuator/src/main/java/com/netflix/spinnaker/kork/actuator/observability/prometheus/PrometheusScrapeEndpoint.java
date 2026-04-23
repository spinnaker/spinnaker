/*
 * Copyright 2025 Harness, Inc.
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
 */

package com.netflix.spinnaker.kork.actuator.observability.prometheus;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Enumeration;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Custom Prometheus scrape endpoint for the observability module.
 *
 * <p>Provides a dedicated endpoint at /actuator/prometheus for Prometheus to scrape metrics. This
 * implementation uses a separate CollectorRegistry to avoid conflicts with Spring Boot's default
 * Prometheus auto-configuration.
 *
 * @see <a
 *     href="https://github.com/spring-projects/spring-boot/blob/main/spring-boot-project/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/metrics/export/prometheus/PrometheusScrapeEndpoint.java">Spring
 *     Boot PrometheusScrapeEndpoint</a>
 */
@WebEndpoint(id = "prometheus")
public class PrometheusScrapeEndpoint {

  private final CollectorRegistry collectorRegistry;

  public PrometheusScrapeEndpoint(CollectorRegistry collectorRegistry) {
    this.collectorRegistry = collectorRegistry;
  }

  // If you use produces = TextFormat.CONTENT_TYPE_004, for some reason the accept header is ignored
  // and a 406 is returned :/
  // So we will use ResponseEntity<String> and set the content type manually.
  @ReadOperation()
  public ResponseEntity<String> scrape() {
    try {
      Writer writer = new StringWriter();
      Enumeration<Collector.MetricFamilySamples> samples =
          this.collectorRegistry.metricFamilySamples();
      TextFormat.write004(writer, samples);

      var responseHeaders = new HttpHeaders();
      responseHeaders.set("Content-Type", TextFormat.CONTENT_TYPE_004);

      return new ResponseEntity<>(writer.toString(), responseHeaders, HttpStatus.OK);
    } catch (IOException ex) {
      // This actually never happens since StringWriter::write() doesn't throw any
      // IOException
      throw new RuntimeException("Writing metrics failed", ex);
    }
  }
}
