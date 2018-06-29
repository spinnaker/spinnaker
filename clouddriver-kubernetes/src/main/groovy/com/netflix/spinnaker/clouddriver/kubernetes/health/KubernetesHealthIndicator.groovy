/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.health

import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import groovy.transform.InheritConstructors
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ResponseStatus

import java.util.concurrent.atomic.AtomicReference

@Component
class KubernetesHealthIndicator implements HealthIndicator {

  private static final Logger LOG = LoggerFactory.getLogger(KubernetesHealthIndicator)

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  private final AtomicReference<Map<String, String>> warningMessages = new AtomicReference<>(null)

  @Override
  Health health() {
    def warnings = warningMessages.get()

    def resultBuilder = new Health.Builder().up()

    warnings.each { k, v -> resultBuilder.withDetail(k, v) }

    return resultBuilder.build()
  }

  @Scheduled(fixedDelay = 300000L)
  void checkHealth() {
    def warnings = [:]

    Set<KubernetesNamedAccountCredentials> kubernetesCredentialsSet = accountCredentialsProvider.all.findAll {
      it instanceof KubernetesNamedAccountCredentials
    } as Set<KubernetesNamedAccountCredentials>

    for (KubernetesNamedAccountCredentials accountCredentials in kubernetesCredentialsSet) {
      try {
        KubernetesCredentials kubernetesCredentials = accountCredentials.credentials
        kubernetesCredentials.getDeclaredNamespaces()
      } catch (Exception ex) {
        warnings.put("kubernetes:${accountCredentials.name}".toString(), ex.message)
      }
    }

    warningMessages.set(warnings)
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Problem communicating with Kubernetes.")
  @InheritConstructors
  static class KubernetesIOException extends RuntimeException {}
}

