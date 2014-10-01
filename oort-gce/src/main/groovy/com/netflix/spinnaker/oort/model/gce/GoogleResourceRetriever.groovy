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

package com.netflix.spinnaker.oort.model.gce

import com.google.api.services.replicapool.ReplicapoolScopes
import com.netflix.frigga.Names
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.oort.security.gce.GoogleCredentials
import org.apache.log4j.Logger

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GoogleResourceRetriever {
  protected static final Logger log = Logger.getLogger(this)

  // TODO(duftler): This should move to a common location.
  private static final String APPLICATION_NAME = "Spinnaker"

  // TODO(duftler): Need to query all regions.
  private static final String REGION = "us-central1"

  // TODO(duftler): Need to query all zones.
  private static final String ZONE = "us-central1-b"

  private static final String GOOGLE_SERVER_GROUP_TYPE = "gce"
  private static final String GOOGLE_INSTANCE_TYPE = "gce"

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

  private static void load(AccountCredentialsProvider accountCredentialsProvider) {
    log.info "Loading GCE resources..."

    def tempAppMap = new HashMap<String, GoogleApplication>()
    def simpleDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    getAllGoogleCredentialsObjects(accountCredentialsProvider).each {
      def accountName = it.key
      def credentialsSet = it.value

      for (GoogleCredentials credentials : credentialsSet) {
        def project = credentials.project
        def credentialBuilder = credentials.createCredentialBuilder(ReplicapoolScopes.REPLICAPOOL)
        def replicapool = new ReplicaPoolBuilder().buildReplicaPool(credentialBuilder, APPLICATION_NAME);
        def poolsListResult = replicapool.pools().list(project, ZONE).execute()

        for (def pool : poolsListResult.resources) {
          def names = Names.parseName(pool.name)
          def appName = names.app.toLowerCase()

          if (appName) {
            if (!tempAppMap[appName]) {
              tempAppMap[appName] = new GoogleApplication(name: appName)
              tempAppMap[appName].clusterNames[accountName] = new HashSet<String>()
              tempAppMap[appName].clusters[accountName] = new HashMap<String, GoogleCluster>()
            }

            if (!tempAppMap[appName].clusters[accountName][names.cluster]) {
              tempAppMap[appName].clusters[accountName][names.cluster] = new GoogleCluster(name: names.cluster, accountName: accountName)
            }

            def cluster = tempAppMap[appName].clusters[accountName][names.cluster]

            // pool.name == names.group
            def googleServerGroup = new GoogleServerGroup(pool.name, GOOGLE_SERVER_GROUP_TYPE, REGION)
            googleServerGroup.zones << ZONE

            def instanceIdSet = new HashSet<>()
            def earliestReplicaTimestamp = Long.MAX_VALUE
            def replicasListResult = replicapool.replicas().list(project, ZONE, pool.name).execute()
            for (def replica : replicasListResult.resources) {
              long replicaTimestamp = replica.status.vmStartTime
                                      ? simpleDateFormat.parse(replica.status.vmStartTime).getTime()
                                      : Long.MAX_VALUE

              // Use earliest replica launchTime as createdTime of replica pool for now.
              earliestReplicaTimestamp = Math.min(earliestReplicaTimestamp, replicaTimestamp)

              def googleInstance = new GoogleInstance(replica.name)
              googleInstance.setProperty("isHealthy", replica.status.state == "RUNNING")
              // oort.aws puts a com.amazonaws.services.ec2.model.Instance here. More importantly, deck expects it.
              googleInstance.setProperty("instance", [instanceId: replica.name,
                                                      instanceType: pool.template.vmParams.machineType,
                                                      providerType: GOOGLE_INSTANCE_TYPE,
                                                      launchTime: replicaTimestamp,
                                                      placement : [availabilityZone: ZONE]])
              googleServerGroup.instances << googleInstance

              instanceIdSet << [instanceId: replica.name]
            }

            // oort.aws puts a com.amazonaws.services.autoscaling.model.AutoScalingGroup here. More importantly, deck expects it.
            googleServerGroup.setProperty("asg", [createdTime: earliestReplicaTimestamp,
                                                  instances  : instanceIdSet,
                                                  minSize    : pool.currentNumReplicas,
                                                  maxSize    : pool.currentNumReplicas,
                                                  desiredCapacity: pool.currentNumReplicas])

            cluster.serverGroups << googleServerGroup
          }
        }
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
