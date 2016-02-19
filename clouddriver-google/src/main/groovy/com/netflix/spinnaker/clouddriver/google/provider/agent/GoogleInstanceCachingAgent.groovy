/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Instance
import com.google.api.services.compute.model.InstancesScopedList
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance2
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleInstanceHealth
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

@Slf4j
class GoogleInstanceCachingAgent extends AbstractGoogleCachingAgent {
  final Set<AgentDataType> providedDataTypes = [
      AUTHORITATIVE.forType(INSTANCES.ns),
      INFORMATIVE.forType(SERVER_GROUPS.ns),
  ]

  String agentType = "${accountName}/global/${GoogleInstanceCachingAgent.simpleName}"

  GoogleInstanceCachingAgent(GoogleCloudProvider googleCloudProvider,
                             String googleApplicationName,
                             String accountName,
                             String project,
                             Compute compute,
                             ObjectMapper objectMapper) {
    this.googleCloudProvider = googleCloudProvider
    this.googleApplicationName = googleApplicationName
    this.accountName = accountName
    this.project = project
    this.compute = compute
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<GoogleInstance2> instances = getInstances()
    buildCacheResults(providerCache, instances)
  }

  List<GoogleInstance2> getInstances() {
    List<GoogleInstance2> instances = new ArrayList<GoogleInstance2>()

    BatchRequest instancesRequest = buildBatchRequest()
    InstanceAggregatedListCallback instancesCallback = new InstanceAggregatedListCallback(instances: instances)
    compute.instances().aggregatedList(project).queue(instancesRequest, instancesCallback)
    executeIfRequestsAreQueued(instancesRequest)

    instances
  }

  CacheResult buildCacheResults(ProviderCache providerCache, List<GoogleInstance2> googleInstances) {
    CacheResultBuilder crb = new CacheResultBuilder()

    googleInstances.each { GoogleInstance2 instance ->
      def instanceKey = Keys.getInstanceKey(googleCloudProvider, accountName, instance.name)
      crb.namespace(INSTANCES.ns).get(instanceKey).with {
        attributes = objectMapper.convertValue(instance, ATTRIBUTES)
      }
    }

    log.info("Caching ${crb.namespace(INSTANCES.ns).size()} instances in ${agentType}")

    crb.build()
  }

  class InstanceAggregatedListCallback<InstanceAggregatedList> extends JsonBatchCallback<InstanceAggregatedList> {

    private static final String GOOGLE_INSTANCE_TYPE = "gce"

    List<GoogleInstance2> instances

    @Override
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.error e.getMessage()
    }

    @Override
    void onSuccess(InstanceAggregatedList instanceAggregatedList, HttpHeaders responseHeaders) throws IOException {
      instanceAggregatedList?.items?.each { String zone, InstancesScopedList instancesScopedList ->
        def localZoneName = Utils.getLocalName(zone)
        instancesScopedList?.instances?.each { Instance instance ->
          long instanceTimestamp = instance.creationTimestamp ?
              Utils.getTimeFromTimestamp(instance.creationTimestamp) :
              Long.MAX_VALUE
          String instanceName = Utils.getLocalName(instance.name)
          def googleInstance = new GoogleInstance2(
              name: instanceName,
              instanceType: Utils.getLocalName(instance.machineType),
              launchTime: instanceTimestamp,
              zone: localZoneName,
              instanceHealth: new GoogleInstanceHealth(
                  status: GoogleInstanceHealth.Status.valueOf(instance.getStatus())
              ))
          instances << googleInstance

          // Set all google-provided attributes for use by non-deck callers.
          instance.keySet().each { key ->
            if (!googleInstance.hasProperty(key)) {
              googleInstance[key] = instance[key]
            }
          }
        }
      }
    }
  }
}
