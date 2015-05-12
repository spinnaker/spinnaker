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
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.services.compute.model.HealthStatus
import com.google.api.services.replicapool.ReplicapoolScopes
import com.google.api.services.replicapool.model.InstanceGroupManager
import com.google.api.services.replicapool.model.InstanceGroupManagerList
import com.netflix.frigga.Names
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.gce.GoogleCredentials
import com.netflix.spinnaker.oort.config.GoogleConfig.GoogleConfigurationProperties
import com.netflix.spinnaker.oort.gce.model.callbacks.ImagesCallback
import com.netflix.spinnaker.oort.gce.model.callbacks.InstanceAggregatedListCallback
import com.netflix.spinnaker.oort.gce.model.callbacks.MIGSCallback
import com.netflix.spinnaker.oort.gce.model.callbacks.NetworkLoadBalancersCallback
import com.netflix.spinnaker.oort.gce.model.callbacks.RegionsCallback
import com.netflix.spinnaker.oort.gce.model.callbacks.Utils
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

import javax.annotation.PostConstruct
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class GoogleResourceRetriever {
  protected final Logger log = Logger.getLogger(GoogleResourceRetriever.class)

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  GoogleConfigurationProperties googleConfigurationProperties

  @Value('${default.build.host:http://builds.netflix.com/}')
  String defaultBuildHost

  protected Lock cacheLock = new ReentrantLock()

  // The value of these fields are always assigned atomically and the collections are never modified after assignment.
  private appMap = new HashMap<String, GoogleApplication>()
  private standaloneInstanceMap = new HashMap<String, List<GoogleInstance>>()
  private imageMap = new HashMap<String, List<Map>>()
  private networkLoadBalancerMap = new HashMap<String, Map<String, List<GoogleLoadBalancer>>>()

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

    cacheLock.lock()

    log.info "Acquired cacheLock for reloading cache."

    try {
      def tempAppMap = new HashMap<String, GoogleApplication>()
      def tempStandaloneInstanceMap = new HashMap<String, List<GoogleInstance>>()
      def tempImageMap = new HashMap<String, List<Map>>()
      def tempNetworkLoadBalancerMap = new HashMap<String, Map<String, List<GoogleLoadBalancer>>>()

      getAllGoogleCredentialsObjects().each {
        def accountName = it.key
        def credentialsSet = it.value

        for (GoogleCredentials credentials : credentialsSet) {
          def project = credentials.project
          def compute = credentials.compute

          BatchRequest regionsBatch = buildBatchRequest(compute)
          BatchRequest migsBatch = buildBatchRequest(compute)
          BatchRequest resourceViewsBatch = buildBatchRequest(compute)
          BatchRequest instancesBatch = buildBatchRequest(compute)
          Map<String, GoogleServerGroup> instanceNameToGoogleServerGroupMap = new HashMap<String, GoogleServerGroup>()

          def credentialBuilder = credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)
          def replicapool = new ReplicaPoolBuilder().buildReplicaPool(credentialBuilder, Utils.APPLICATION_NAME)
          def regions = compute.regions().list(project).execute().getItems()
          def regionsCallback = new RegionsCallback(tempAppMap,
                                                    accountName,
                                                    project,
                                                    compute,
                                                    credentialBuilder,
                                                    replicapool,
                                                    tempImageMap,
                                                    defaultBuildHost,
                                                    instanceNameToGoogleServerGroupMap,
                                                    migsBatch,
                                                    resourceViewsBatch)

          regions.each { region ->
            compute.regions().get(project, region.getName()).queue(regionsBatch, regionsCallback)
          }

          // Image lists are keyed by account in imageMap.
          if (!tempImageMap[accountName]) {
            tempImageMap[accountName] = new ArrayList<Map>()
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
            tempNetworkLoadBalancerMap[accountName] = new HashMap<String, List<GoogleLoadBalancer>>()
          }

          def instanceNameToLoadBalancerHealthStatusMap = new HashMap<String, Map<String, List<HealthStatus>>>()

          // Retrieve all available network load balancers for this project.
          def networkLoadBalancersCallback = new NetworkLoadBalancersCallback(tempNetworkLoadBalancerMap[accountName],
                                                                              instanceNameToLoadBalancerHealthStatusMap,
                                                                              accountName,
                                                                              project,
                                                                              compute,
                                                                              migsBatch,
                                                                              resourceViewsBatch)

          compute.forwardingRules().aggregatedList(project).queue(regionsBatch, networkLoadBalancersCallback)

          executeIfRequestsAreQueued(regionsBatch)
          executeIfRequestsAreQueued(migsBatch)
          executeIfRequestsAreQueued(resourceViewsBatch)

          // Standalone instance maps are keyed by account in standaloneInstanceMap.
          if (!tempStandaloneInstanceMap[accountName]) {
            tempStandaloneInstanceMap[accountName] = new ArrayList<GoogleInstance>()
          }

          def instanceAggregatedListCallback =
            new InstanceAggregatedListCallback(instanceNameToGoogleServerGroupMap,
                                               tempStandaloneInstanceMap[accountName],
                                               instanceNameToLoadBalancerHealthStatusMap)

          compute.instances().aggregatedList(project).queue(instancesBatch, instanceAggregatedListCallback)
          executeIfRequestsAreQueued(instancesBatch)
        }
      }

      populateLoadBalancerServerGroups(tempAppMap, tempNetworkLoadBalancerMap)

      appMap = tempAppMap
      standaloneInstanceMap = tempStandaloneInstanceMap
      imageMap = tempImageMap
      networkLoadBalancerMap = tempNetworkLoadBalancerMap
    } finally {
      cacheLock.unlock()
    }

    log.info "Finished loading GCE resources."
  }

  public static void populateLoadBalancerServerGroups(HashMap<String, GoogleApplication> tempAppMap,
                                                      Map<String, Map<String, List<GoogleLoadBalancer>>> tempNetworkLoadBalancerMap) {
    // Build a reverse index from load balancers to server groups.
    // First level is keyed by accountName and second level is keyed by load balancer name.
    // Value at end of path is a list of google server groups.
    def loadBalancerNameToServerGroupsMap = [:].withDefault { [:].withDefault { [] } }

    tempAppMap.each { applicationName, googleApplication ->
      googleApplication.clusters.each { accountName, clusterMap ->
        clusterMap.each { clusterName, googleCluster ->
          googleCluster.serverGroups.each { googleServerGroup ->
            def loadBalancerNames = googleServerGroup.getLoadBalancers()

            loadBalancerNames.each { loadBalancerName ->
              loadBalancerNameToServerGroupsMap[accountName][loadBalancerName] << googleServerGroup
            }
          }
        }
      }
    }

    // Populate each load balancer with its summary server group and instance data.
    loadBalancerNameToServerGroupsMap.each { accountName, lbNameToServerGroupsMap ->
      lbNameToServerGroupsMap.each { loadBalancerName, serverGroupList ->
        serverGroupList.each { serverGroup ->
          def loadBalancer = tempNetworkLoadBalancerMap[accountName]?.get(serverGroup.getRegion())?.find {
            it.name == loadBalancerName
          }

          def instances = [] as Set

          serverGroup.instances.each { instance ->
            // Only include the instances from the server group that are also registered with the load balancer.
            if (loadBalancer["instanceNames"].contains(instance.name)) {
              // Only include the health returned by this load balancer.
              def loadBalancerHealth = instance.health.find {
                it.type == "LoadBalancer"
              }?.loadBalancers?.find {
                it.loadBalancerName == loadBalancerName
              }

              def health = loadBalancerHealth
                           ? [
                             state      : loadBalancerHealth.state,
                             description: loadBalancerHealth.description
                           ]
                           : [
                             state      : "Unknown",
                             description: "Unable to determine load balancer health."
                           ]

              instances << [
                id    : instance.name,
                zone  : Utils.getLocalName(instance.getZone()),
                health: health
              ]
            }
          }

          def serverGroupSummary = [
            name      : serverGroup.name,
            isDisabled: serverGroup.isDisabled(),
            instances : instances
          ]

          loadBalancer?.serverGroups << serverGroupSummary
        }
      }
    }
  }

  private static executeIfRequestsAreQueued(BatchRequest batch) {
    if (batch.size()) {
      batch.execute()
    }
  }

  private static BatchRequest buildBatchRequest(def compute) {
    return compute.batch(
      new HttpRequestInitializer() {
        @Override
        void initialize(HttpRequest request) throws IOException {
          request.headers.setUserAgent(Utils.APPLICATION_NAME);
        }
      }
    )
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

  void handleCacheUpdate(Map<String, ? extends Object> data) {
    log.info "Refreshing cache for server group $data.serverGroupName in account $data.account..."

    if (cacheLock.tryLock()) {
      log.info "Acquired cacheLock for updating cache."

      try {
        def accountCredentials = accountCredentialsProvider.getCredentials(data.account)

        if (accountCredentials?.credentials instanceof GoogleCredentials) {
          GoogleCredentials credentials = accountCredentials.credentials

          def project = credentials.project
          def compute = credentials.compute

          BatchRequest resourceViewsBatch = buildBatchRequest(compute)
          BatchRequest instancesBatch = buildBatchRequest(compute)

          def credentialBuilder = credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)
          def replicapool = new ReplicaPoolBuilder().buildReplicaPool(credentialBuilder, Utils.APPLICATION_NAME)

          def tempAppMap = new HashMap<String, GoogleApplication>()
          def instanceNameToGoogleServerGroupMap = new HashMap<String, GoogleServerGroup>()
          def migsCallback = new MIGSCallback(tempAppMap,
                                              data.region,
                                              data.zone,
                                              data.account,
                                              project,
                                              compute,
                                              credentialBuilder,
                                              imageMap,
                                              defaultBuildHost,
                                              instanceNameToGoogleServerGroupMap,
                                              resourceViewsBatch)

          // Handle 404 here (especially when this is called after destroying a replica pool).
          InstanceGroupManager instanceGroupManager = null

          try {
            instanceGroupManager = replicapool.instanceGroupManagers().get(project, data.zone, data.serverGroupName).execute()
          } catch (GoogleJsonResponseException e) {
            // Nothing to do here except leave instanceGroupManager null. 404 can land us here.
          }

          // If the InstanceGroupManager was returned, query all of its details and instances.
          if (instanceGroupManager) {
            InstanceGroupManagerList instanceGroupManagerList = new InstanceGroupManagerList(items: [instanceGroupManager])

            migsCallback.onSuccess(instanceGroupManagerList, null)

            executeIfRequestsAreQueued(resourceViewsBatch)
            executeIfRequestsAreQueued(instancesBatch)
          }

          // Apply the naming-convention to derive application and cluster names from server group name.
          Names names = Names.parseName(data.serverGroupName)
          def appName = names.app.toLowerCase()
          def clusterName = names.cluster

          // Attempt to retrieve the requested server group from the containing cluster.
          GoogleCluster cluster = Utils.retrieveOrCreatePathToCluster(tempAppMap, data.account, appName, clusterName)
          GoogleServerGroup googleServerGroup = cluster.serverGroups.find { googleServerGroup ->
            googleServerGroup.name == data.serverGroupName
          }

          // Now update the cache with the new information.
          createUpdatedApplicationMap(data.account, data.serverGroupName, googleServerGroup)

          log.info "Finished refreshing cache for server group $data.serverGroupName in account $data.account."
        }
      } finally {
        cacheLock.unlock()
      }
    } else {
      log.info "Unable to acquire cacheLock for updating cache."
    }
  }

  /*
   * Clone the existing map and either update or remove the specified server group. The parameters accountName and
   * serverGroupName are required, but newGoogleServerGroup can be null.
   */
  void createUpdatedApplicationMap(String accountName, String serverGroupName, GoogleServerGroup newGoogleServerGroup) {
    // Clone the map prior to mutating it.
    def tempMap = Utils.deepCopyApplicationMap(appMap)

    // Apply the naming-convention to derive application and cluster names from server group name.
    def names = Names.parseName(serverGroupName)
    def appName = names.app.toLowerCase()
    def clusterName = names.cluster

    // Retrieve the containing cluster in the newly-cloned map.
    GoogleCluster cluster = Utils.retrieveOrCreatePathToCluster(tempMap, accountName, appName, clusterName)

    // Find any matching server groups in the cluster.
    def oldGoogleServerGroupsToRemove = cluster.serverGroups.findAll { existingGoogleServerGroup ->
      existingGoogleServerGroup.name == serverGroupName
    }

    // Remove any matches.
    cluster.serverGroups -= oldGoogleServerGroupsToRemove

    // If a newly-retrieved server group exists, add it to the containing cluster in the newly-cloned map.
    if (newGoogleServerGroup) {
      cluster.serverGroups << newGoogleServerGroup
    }

    appMap = tempMap
  }

  Map<String, GoogleApplication> getApplicationsMap() {
    return appMap
  }

  Map<String, List<GoogleInstance>> getStandaloneInstanceMap() {
    return standaloneInstanceMap
  }

  Map<String, List<Map>> getImageMap() {
    return imageMap
  }

  Map<String, Map<String, List<GoogleLoadBalancer>>> getNetworkLoadBalancerMap() {
    return networkLoadBalancerMap
  }
}
