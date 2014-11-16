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

import com.google.api.services.replicapool.ReplicapoolScopes
import com.netflix.frigga.Names
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.gce.GoogleCredentials
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

  // TODO(duftler): Handle paginated results.
  // TODO(duftler): Batch calls.
  private static void load(AccountCredentialsProvider accountCredentialsProvider) {
    log.info "Loading GCE resources..."

    def tempAppMap = new HashMap<String, GoogleApplication>()
    def simpleDateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")

    getAllGoogleCredentialsObjects(accountCredentialsProvider).each {
      def accountName = it.key
      def credentialsSet = it.value

      for (GoogleCredentials credentials : credentialsSet) {
        def project = credentials.project
        def credentialBuilder = credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)
        def replicapool = new ReplicaPoolBuilder().buildReplicaPool(credentialBuilder, APPLICATION_NAME)
        def zones = credentials.compute.regions().get(project, REGION).execute().getZones()

        zones.each { zone ->
        def localZoneName = getLocalName(zone)
        def instanceGroupManagersListResult = replicapool.instanceGroupManagers().list(project, localZoneName).execute()

        for (def instanceGroupManager : instanceGroupManagersListResult.items) {
          def names = Names.parseName(instanceGroupManager.name)
          def appName = names.app.toLowerCase()

          def instanceTemplateName = getLocalName(instanceGroupManager.instanceTemplate)
          def instanceTemplate = credentials.compute.instanceTemplates().get(project, instanceTemplateName).execute()

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

            tempAppMap[appName].clusterNames[accountName] << names.cluster

            // instanceGroupManager.name == names.group
            def googleServerGroup = new GoogleServerGroup(instanceGroupManager.name, GOOGLE_SERVER_GROUP_TYPE, REGION)
            googleServerGroup.zones << localZoneName

            def earliestReplicaTimestamp = Long.MAX_VALUE

            def resourceViews = new ResourceViewsBuilder().buildResourceViews(credentialBuilder, APPLICATION_NAME)
            def listResourcesResult = resourceViews.zoneViews().listResources(project, localZoneName, instanceGroupManager.name).execute()

            for (def listResource : listResourcesResult.getItems()) {
              def instanceName = getLocalName(listResource.resource)
              def instance = credentials.compute.instances().get(project, localZoneName, instanceName).execute()
              long instanceTimestamp = instance.creationTimestamp
                                       ? simpleDateFormat.parse(instance.creationTimestamp).getTime()
                                       : Long.MAX_VALUE
              boolean instanceIsHealthy = instance.status == "RUNNING"

              // Use earliest replica launchTime as createdTime of instance group for now.
              earliestReplicaTimestamp = Math.min(earliestReplicaTimestamp, instanceTimestamp)

              def googleInstance = new GoogleInstance(instance.name)
              googleInstance.setProperty("isHealthy", instanceIsHealthy)
              googleInstance.setProperty("instanceId", instance.name)
              googleInstance.setProperty("instanceType", instanceTemplate.properties.machineType)
              googleInstance.setProperty("providerType", GOOGLE_INSTANCE_TYPE)
              googleInstance.setProperty("launchTime", instanceTimestamp)
              googleInstance.setProperty("placement", [availabilityZone: localZoneName])
              googleInstance.setProperty("health", [[type: "GCE",
                                                     state: instanceIsHealthy ? "Up" : "Down"]])
              googleServerGroup.instances << googleInstance
            }

            // oort.aws puts a com.amazonaws.services.autoscaling.model.AutoScalingGroup here. More importantly, deck expects it.
            googleServerGroup.setProperty("asg", [minSize    : instanceGroupManager.targetSize,
                                                  maxSize    : instanceGroupManager.targetSize,
                                                  desiredCapacity: instanceGroupManager.targetSize])

            googleServerGroup.setProperty("launchConfig", [createdTime: earliestReplicaTimestamp])

            cluster.serverGroups << googleServerGroup
          }
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

  private static String getLocalName(String fullUrl) {
    int lastIndex = fullUrl.lastIndexOf('/')

    return lastIndex != -1 ? fullUrl.substring(lastIndex + 1) : fullUrl
  }
}
