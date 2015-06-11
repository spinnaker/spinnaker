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

import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Component
class EndpointMonitorManager implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(EndpointMonitorManager)

  ScheduledExecutorService scheduledExecutorService
  EndpointMonitor endpointMonitor
  MonitoredEndpoints monitoredEndpoints
  MetricsLogger metricsLogger

  @Autowired
  EndpointMonitorManager(ScheduledExecutorService scheduledExecutorService, EndpointMonitor endpointMonitor, MonitoredEndpoints monitoredEndpoints, MetricsLogger metricsLogger) {
    this.scheduledExecutorService = scheduledExecutorService
    this.endpointMonitor = endpointMonitor
    this.monitoredEndpoints = monitoredEndpoints
    this.metricsLogger = metricsLogger
  }

  @PostConstruct
  void init() {
    scheduledExecutorService.scheduleAtFixedRate(this, 0, 30, TimeUnit.SECONDS)
  }

  @PreDestroy
  void shutdown() {
    scheduledExecutorService.shutdownNow()
  }

  void run() {
    try {
      String disco = "${monitoredEndpoints.discoveryUrl}/v2/vips/${monitoredEndpoints.vipName}"
      def applications = new JsonSlurper().parseText(disco.toURL().getText(requestProperties: [Accept: 'application/json']))
      List<URI> instances = applications.applications.application.instance.findAll {
        it.status == 'UP' && it.port.'@enabled' == 'true'
      }.collect {
        it.homePageUrl.toURI()
      }
      def results = []
      for (path in monitoredEndpoints.paths) {
        for (instance in instances) {
          def uri = instance.resolve(path)
          LOG.info("Checking ${uri}")
          results << endpointMonitor.call(uri)
        }
      }
      metricsLogger.log(results)
    } catch (Throwable t) {
      LOG.error("Failed this iteration", t)
    }
  }

}
