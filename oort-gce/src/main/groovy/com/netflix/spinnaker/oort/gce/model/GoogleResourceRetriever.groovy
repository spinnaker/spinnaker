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
import com.netflix.spinnaker.oort.gce.model.callbacks.RegionsCallback
import com.netflix.spinnaker.oort.gce.model.callbacks.Utils
import org.apache.log4j.Logger

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GoogleResourceRetriever {
  protected static final Logger log = Logger.getLogger(this)

  // The value of this field is always assigned atomically and the map is never modified after assignment.
  private static appMap = new HashMap<String, GoogleApplication>()
  private static AtomicBoolean initialized = new AtomicBoolean(false)

  static void init(AccountCredentialsProvider accountCredentialsProvider) {
    // We want this routine to run exactly once.
    if (initialized.compareAndSet(false, true)) {
      log.info "Initializing GoogleResourceRetriever thread..."

      // Load all resources initially in 10 seconds, and then every 60 seconds thereafter.
      Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
        try {
          load(accountCredentialsProvider)
        } catch (Throwable t) {
          t.printStackTrace()
        }
      }, 10, 60, TimeUnit.SECONDS)
    }
  }

  // TODO(duftler): Handle paginated results.
  private static void load(AccountCredentialsProvider accountCredentialsProvider) {
    log.info "Loading GCE resources..."

    def tempAppMap = new HashMap<String, GoogleApplication>()

    getAllGoogleCredentialsObjects(accountCredentialsProvider).each {
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

        regionsBatch.execute()
        migsBatch.execute()
        resourceViewsBatch.execute()
        instancesBatch.execute()
      }
    }

    appMap = tempAppMap

    log.info "Finished loading GCE resources."
  }

  static Map<String, Set<GoogleCredentials>> getAllGoogleCredentialsObjects(AccountCredentialsProvider accountCredentialsProvider) {
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
}
