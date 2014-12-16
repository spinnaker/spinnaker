/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.oort.gce.model

import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.services.replicapool.ReplicapoolScopes
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.oort.config.GoogleConfig.GoogleConfigurationProperties
import com.netflix.spinnaker.oort.gce.model.callbacks.ImagesCallback
import com.netflix.spinnaker.oort.gce.model.callbacks.NetworkLoadBalancersCallback
import com.netflix.spinnaker.oort.gce.model.callbacks.RegionsCallback
import com.netflix.spinnaker.oort.gce.model.callbacks.Utils
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired

import javax.annotation.PostConstruct
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GoogleResourceRetriever {
  protected final Logger log = Logger.getLogger(GoogleResourceRetriever.class)

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  GoogleConfigurationProperties googleConfigurationProperties

  // The value of these fields are always assigned atomically and the collections are never modified after assignment.
  private appMap = new HashMap<String, GoogleApplication>()
  private imageMap = new HashMap<String, List<String>>()
  private networkLoadBalancerMap = new HashMap<String, Map<String, List<String>>>()

  @PostConstruct
  void init() {
    log.info "Initializing GoogleResourceRetriever thread..."

    // Load all resources initially in 10 seconds, and then every googleConfigurationProperties.pollingIntervalSeconds seconds thereafter.
    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
      try {
        load()
      } catch (Throwable t) {
        t.printStackTrace()
      }
    }, 10, googleConfigurationProperties.pollingIntervalSeconds, TimeUnit.SECONDS)
  }

  // TODO(duftler): Handle paginated results.
  private void load() {
    log.info "Loading GCE resources..."

    def tempAppMap = new HashMap<String, GoogleApplication>()
    def tempImageMap = new HashMap<String, List<String>>()
    def tempNetworkLoadBalancerMap = new HashMap<String, Map<String, List<String>>>()

    getAllGoogleCredentialsObjects().each {
      def accountName = it.key
      def credentialsSet = it.value

      for (GoogleCredentials credentials : credentialsSet) {
        def project = credentials.project
        def compute = credentials.compute

        BatchRequest regionsBatch = compute.batch()
        BatchRequest migsBatch = compute.batch()
        BatchRequest resourceViewsBatch = compute.batch()
        BatchRequest instancesBatch = compute.batch()

        def credentialBuilder = credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)
        def replicapool = new ReplicaPoolBuilder().buildReplicaPool(credentialBuilder, Utils.APPLICATION_NAME)
        def regions = compute.regions().list(project).execute().getItems()
        def regionsCallback = new RegionsCallback(tempAppMap,
                                                  accountName,
                                                  project,
                                                  compute,
                                                  credentialBuilder,
                                                  replicapool,
                                                  migsBatch,
                                                  resourceViewsBatch,
                                                  instancesBatch)

        regions.each { region ->
          compute.regions().get(project, region.getName()).queue(regionsBatch, regionsCallback)
        }

        // Image lists are keyed by account in imageMap.
        if (!tempImageMap[accountName]) {
          tempImageMap[accountName] = new ArrayList<String>()
        }

        // Retrieve all available images for this project.
        compute.images().list(project).queue(regionsBatch, new ImagesCallback(tempImageMap[accountName], false))

        // Retrieve pruned list of available images for known public image projects.
        def imagesCallback = new ImagesCallback(tempImageMap[accountName], true)

        Utils.baseImageProjects.each { imageProject ->
          compute.images().list(imageProject).queue(regionsBatch, imagesCallback)
        }

        // Network load balancer maps are keyed by account in networkLoadBalancerMap.
        if (!tempNetworkLoadBalancerMap[accountName]) {
          tempNetworkLoadBalancerMap[accountName] = new HashMap<String, List<String>>()
        }

        // Retrieve all available network load balancers for this project.
        compute.forwardingRules().aggregatedList(project).queue(
          regionsBatch, new NetworkLoadBalancersCallback(tempNetworkLoadBalancerMap[accountName]))


        executeIfRequestsAreQueued(regionsBatch)
        executeIfRequestsAreQueued(migsBatch)
        executeIfRequestsAreQueued(resourceViewsBatch)
        executeIfRequestsAreQueued(instancesBatch)
      }
    }

    appMap = tempAppMap
    imageMap = tempImageMap
    networkLoadBalancerMap = tempNetworkLoadBalancerMap

    log.info "Finished loading GCE resources."
  }

  private static executeIfRequestsAreQueued(BatchRequest batch) {
    if (batch.size()) {
      batch.execute()
    }
  }

  Map<String, Set<GoogleCredentials>> getAllGoogleCredentialsObjects() {
    def accountNameToSetOfGoogleCredentialsMap = new HashMap<String, Set<GoogleCredentials>>()

    for (def accountCredentials : accountCredentialsProvider.getAll()) {
      try {
        if (accountCredentials.credentials instanceof GoogleCredentials) {
          def accountName = accountCredentials.getName()

          if (!accountNameToSetOfGoogleCredentialsMap.containsKey(accountName)) {
            accountNameToSetOfGoogleCredentialsMap[accountName] = new HashSet<GoogleCredentials>()
          }

          accountNameToSetOfGoogleCredentialsMap[accountName] << accountCredentials.credentials
        }
      } catch (e) {
        log.info "Squashed exception ${e.getClass().getName()} thrown by $accountCredentials."
      }
    }

    accountNameToSetOfGoogleCredentialsMap
  }

  Map<String, GoogleApplication> getApplicationsMap() {
    return appMap
  }

  Map<String, List<String>> getImageMap() {
    return imageMap
  }

  Map<String, Map<String, List<String>>> getNetworkLoadBalancerMap() {
    return networkLoadBalancerMap
  }
}
