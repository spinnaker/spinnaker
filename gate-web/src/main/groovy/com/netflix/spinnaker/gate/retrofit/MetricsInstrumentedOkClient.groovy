/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.retrofit

import com.google.common.base.Stopwatch
import com.netflix.spectator.api.ExtendedRegistry
import com.netflix.spectator.api.Id
import com.netflix.spinnaker.kork.metrics.SpectatorMetricWriter
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.metrics.Metric
import org.springframework.boot.actuate.metrics.repository.MetricRepository
import retrofit.client.*


import static java.util.concurrent.TimeUnit.MILLISECONDS

@CompileStatic
class MetricsInstrumentedOkClient extends OkClient {
  //private final Id id
  private final String name
  private final MetricRepository metricRepository

  MetricsInstrumentedOkClient(MetricRepository metricRepository, String name) {
    this.metricRepository = metricRepository
    this.name = name
    //this.id = extendedRegistry.createId("service.latency.${name}".toString())
  }

  @Override
  public Response execute(Request req) throws IOException {
    def stopwatch = new Stopwatch().start()
    try {
      return super.execute(req)
    } finally {
      stopwatch.stop()
      def url = req.url.replaceAll("niws", "http").toURL()
      def path = "${url.path}${url.query ? '?'+url.query : ''}"
      def latency = stopwatch.elapsed(MILLISECONDS)
      metricRepository.set(new Metric<Number>("guage.service.latency.${name}.${path}".toString(), latency))
    }
  }
}
