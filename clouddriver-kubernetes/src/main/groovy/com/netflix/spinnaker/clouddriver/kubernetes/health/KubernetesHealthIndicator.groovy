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
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceBuilder
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

  private final AtomicReference<Exception> lastException = new AtomicReference<>(null)

  @Override
  Health health() {
    def ex = lastException.get()

    if (ex) {
      throw ex
    }

    new Health.Builder().up().build()
  }

  @Scheduled(fixedDelay = 300000L)
  void checkHealth() {
    try {
      Set<KubernetesNamedAccountCredentials> kubernetesCredentialsSet = accountCredentialsProvider.all.findAll {
        it instanceof KubernetesNamedAccountCredentials
      } as Set<KubernetesNamedAccountCredentials>

      for (KubernetesNamedAccountCredentials accountCredentials in kubernetesCredentialsSet) {
        KubernetesCredentials kubernetesCredentials = accountCredentials.credentials

        // This verifies that the specified credentials are sufficient to
        // access the referenced Kubernetes master endpoint.
        kubernetesCredentials.getNamespaces().each { namespace ->
          Namespace res = kubernetesCredentials.apiAdaptor.getNamespace(namespace)
          if (res == null) {
            NamespaceBuilder namespaceBuilder = new NamespaceBuilder();
            Namespace newNamespace = namespaceBuilder.withNewMetadata().withName(namespace).endMetadata().build()
            kubernetesCredentials.apiAdaptor.createNamespace(newNamespace)
            LOG.info "Created missing namespace $namespace"
          }
        }
      }

      lastException.set(null)
    } catch (Exception ex) {
      LOG.warn "Unhealthy", ex

      lastException.set(ex)
    }
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Problem communicating with Kubernetes.")
  @InheritConstructors
  static class KubernetesIOException extends RuntimeException {}
}

