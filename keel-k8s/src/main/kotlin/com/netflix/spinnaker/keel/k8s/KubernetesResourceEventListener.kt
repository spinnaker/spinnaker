/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.k8s

import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceEventType.CREATE
import com.netflix.spinnaker.keel.events.ResourceEventType.DELETE
import com.netflix.spinnaker.keel.events.ResourceEventType.UPDATE
import io.kubernetes.client.ApiException
import io.kubernetes.client.apis.CustomObjectsApi
import io.kubernetes.client.models.V1DeleteOptions
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class KubernetesResourceEventListener(
  private val customObjectsApi: CustomObjectsApi
) {

  @EventListener
  fun handle(event: ResourceEvent) {
    log.info("Received event {}", event)
    try {
      when (event.type) {
        CREATE -> customObjectsApi
          .createClusterCustomObject(
            event.resource.apiVersion.group,
            event.resource.apiVersion.version,
            event.resource.kind.substringBefore(".") + "s",
            event.resource,
            "true"
          )
        UPDATE -> customObjectsApi
          .patchClusterCustomObject(
            event.resource.apiVersion.group,
            event.resource.apiVersion.version,
            event.resource.kind.substringBefore(".") + "s",
            event.resource.metadata.name.value,
            event.resource
          )
        DELETE -> customObjectsApi
          .deleteClusterCustomObject(
            event.resource.apiVersion.group,
            event.resource.apiVersion.version,
            event.resource.kind.substringBefore(".") + "s",
            event.resource.metadata.name.value,
            V1DeleteOptions(),
            0,
            null,
            "Background"

          )
        else -> TODO()
      }
    } catch (e: ApiException) {
      log.error("Exception sending event to Kubernetes: {} {}", e.code, e.responseBody)
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
