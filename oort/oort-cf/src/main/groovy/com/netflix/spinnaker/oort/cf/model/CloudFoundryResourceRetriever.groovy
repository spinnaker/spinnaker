/*
 * Copyright 2015 Pivotal Inc.
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

package com.netflix.spinnaker.oort.cf.model

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConfigurationProperties
import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConstants
import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.oort.model.HealthState
import groovy.util.logging.Slf4j
import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.CloudFoundryException
import org.cloudfoundry.client.lib.domain.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpServerErrorException

import javax.annotation.PostConstruct
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

@Slf4j
class CloudFoundryResourceRetriever {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties

  protected Lock cacheLock = new ReentrantLock()

  Map<String, CloudSpace> spaceCache = new HashMap<>()
  Map<String, Set<CloudService>> serviceCache = [:].withDefault { [] as Set<CloudService> }
  Set<CloudRoute> routeCache = [] as Set<CloudRoute>

  Map<String, Set<CloudFoundryServerGroup>> serverGroupsByAccount = [:].withDefault {[] as Set<CloudFoundryServerGroup>}
  Map<String, Map<String, CloudFoundryServerGroup>> serverGroupByAccountAndServerGroupName = [:].withDefault {[:]}
  Map<String, Map<String, Set<CloudFoundryServerGroup>>> serverGroupsByAccountAndClusterName =
      [:].withDefault {[:].withDefault {[] as Set<CloudFoundryServerGroup>}}

  Map<String, Set<CloudFoundryCluster>> clustersByApplicationName = [:].withDefault {[] as Set<CloudFoundryCluster>}
  Map<String, Map<String, Set<CloudFoundryCluster>>> clustersByApplicationAndAccount =
      [:].withDefault {[:].withDefault {[] as Set<CloudFoundryCluster>}}
  Map<String, Map<String, CloudFoundryCluster>> clusterByAccountAndClusterName =
      [:].withDefault {[:].withDefault {new CloudFoundryCluster()}}
  Map<String, Set<CloudFoundryCluster>> clustersByAccount = [:].withDefault {[] as Set<CloudFoundryCluster>}

  Set<CloudFoundryService> services = [] as Set<CloudFoundryService>
  Map<String, Set<CloudFoundryService>> servicesByAccount = [:].withDefault {[] as Set<CloudFoundryService>}
  Map<String, Set<CloudFoundryService>> servicesByRegion = [:].withDefault {[] as Set<CloudFoundryService>}

  Map<String, CloudFoundryApplication> applicationByName = [:].withDefault {new CloudFoundryApplication()}

  Map<String, Set<CloudFoundryLoadBalancer>> loadBalancersByAccount = [:].withDefault {[] as Set<CloudFoundryLoadBalancer>}
  Map<String, Set<CloudFoundryLoadBalancer>> loadBalancersByApplication = [:].withDefault {[] as Set<CloudFoundryLoadBalancer>}
  Map<String, Map<String, Set<CloudFoundryLoadBalancer>>> loadBalancersByAccountAndClusterName =
      [:].withDefault {[:].withDefault {[] as Set<CloudFoundryLoadBalancer>}}

  Map<String, Map<String, CloudFoundryApplicationInstance>> instancesByAccountAndId =
          [:].withDefault {[:] as Map<String, CloudFoundryApplicationInstance>}

  @PostConstruct
  void init() {
    log.info "Initializing CloudFoundryResourceRetriever thread..."

    int initialTimeToLoadSeconds = 15

    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
      try {
        load()
      } catch (Throwable t) {
        t.printStackTrace()
      }
    }, initialTimeToLoadSeconds, cloudFoundryConfigurationProperties.pollingIntervalSeconds, TimeUnit.SECONDS)
  }

  private void load() {
    log.info "Loading CF resources..."

    cacheLock.lock()

    log.info "Acquired cacheLock for reloading cache."

    try {

      Map<String, CloudSpace> tempSpaceCache = new HashMap<>()
      Map<String, Set<CloudService>> tempServiceCache = [:].withDefault { [] as Set<CloudService> }
      Set<CloudRoute> tempRouteCache = [] as Set<CloudRoute>

      Map<String, Set<CloudFoundryServerGroup>> tempServerGroupsByAccount = [:].withDefault {[] as Set<CloudFoundryServerGroup>}
      Map<String, Map<String, CloudFoundryServerGroup>> tempServerGroupByAccountAndServerGroupName = [:].withDefault {[:]}
      Map<String, Map<String, Set<CloudFoundryServerGroup>>> tempServerGroupsByAccountAndClusterName =
          [:].withDefault {[:].withDefault {[] as Set<CloudFoundryServerGroup>}}

      Map<String, Set<CloudFoundryCluster>> tempClustersByApplicationName = [:].withDefault {[] as Set<CloudFoundryCluster>}
      Map<String, Map<String, Set<CloudFoundryCluster>>> tempClustersByApplicationAndAccount =
          [:].withDefault {[:].withDefault {[] as Set<CloudFoundryCluster>}}
      Map<String, Map<String, CloudFoundryCluster>> tempClusterByAccountAndClusterName =
          [:].withDefault {[:].withDefault {new CloudFoundryCluster()}}
      Map<String, Set<CloudFoundryCluster>> tempClustersByAccount = [:].withDefault {[] as Set<CloudFoundryCluster>}

      Set<CloudFoundryService> tempServices = [] as Set<CloudFoundryService>
      Map<String, Set<CloudFoundryService>> tempServicesByAccount = [:].withDefault {[] as Set<CloudFoundryService>}
      Map<String, Set<CloudFoundryService>> tempServicesByRegion = [:].withDefault {[] as Set<CloudFoundryService>}

      Map<String, CloudFoundryApplication> tempApplicationByName = [:].withDefault {new CloudFoundryApplication()}

      Map<String, Set<CloudFoundryLoadBalancer>> tempLoadBalancersByAccount = [:].withDefault {[] as Set<CloudFoundryLoadBalancer>}
      Map<String, Set<CloudFoundryLoadBalancer>> tempLoadBalancersByApplication = [:].withDefault {[] as Set<CloudFoundryLoadBalancer>}
      Map<String, Map<String, Set<CloudFoundryLoadBalancer>>> tempLoadBalancersByAccountAndClusterName =
          [:].withDefault {[:].withDefault {[] as Set<CloudFoundryLoadBalancer>}}

      Map<String, Map<String, CloudFoundryApplicationInstance>> tempInstancesByAccountAndId =
          [:].withDefault {[:] as Map<String, CloudFoundryApplicationInstance>}

      accountCredentialsProvider.all.each { accountCredentials ->
        try {
          if (accountCredentials instanceof CloudFoundryAccountCredentials) {
            CloudFoundryAccountCredentials credentials = (CloudFoundryAccountCredentials) accountCredentials
            log.info "Logging in to ${credentials.api} as ${credentials.name}"

            def client = new CloudFoundryClient(credentials.credentials, credentials.api.toURL(), true)

            log.info "Looking up spaces..."
            client.spaces.each { space ->
              if (!tempSpaceCache.containsKey(space.meta.guid)) {
                tempSpaceCache.put(space.meta.guid, space)
              }
            }

            log.info "Looking up services..."
            tempSpaceCache.values().each { space ->
              def conn = new CloudFoundryClient(credentials.credentials, credentials.api.toURL(), space, true)
              conn.services.each { service ->
                tempServiceCache.get(space.meta.guid).add(service)
              }
              conn.logout()
            }

            def space = tempSpaceCache.values().find {
              it?.name == credentials.space && it?.organization.name == credentials.org
            }
            client = new CloudFoundryClient(credentials.credentials, credentials.api.toURL(), space, true)

            log.info "Looking up routes..."
            client.getDomainsForOrg().each { domain ->
              client.getRoutes(domain.name).each { route ->
                tempRouteCache.add(route)
              }
            }

            log.info "Look up all applications..."
            def cfApplications = client.applications

            cfApplications.each { app ->

              app.space = space
              Names names = Names.parseName(app.name)

              def serverGroup = lookupServerGroup(client, credentials, app, names, space, tempServiceCache,
                  tempRouteCache,
                  tempServerGroupsByAccount,
                  tempServerGroupByAccountAndServerGroupName,
                  tempServerGroupsByAccountAndClusterName,
                  tempLoadBalancersByAccount, tempLoadBalancersByApplication,
                  tempLoadBalancersByAccountAndClusterName, tempInstancesByAccountAndId)

              tempServices.addAll(serverGroup.services)
              tempServicesByAccount[credentials.name].addAll(serverGroup.services)
              tempServicesByRegion[space.organization.name].addAll(serverGroup.services)

              def cluster = tempClusterByAccountAndClusterName[credentials.name][names.cluster]
              cluster.name = names.cluster
              cluster.accountName = credentials.name
              cluster.serverGroups.removeAll {it.name == serverGroup.name}
              cluster.serverGroups.add(serverGroup)
              cluster.loadBalancers.addAll(serverGroup.nativeLoadBalancers)

              tempClustersByApplicationName[names.app].add(cluster)
              tempClustersByApplicationAndAccount[names.app][credentials.name].add(cluster)
              tempClustersByAccount[credentials.name].add(cluster)

              def application = tempApplicationByName[names.app]
              application.name = names.app
              application.applicationClusters[credentials.name].add(cluster)
              application.clusterNames[credentials.name].add(cluster.name)

            }

            log.info "Done loading new version of data"

          }
        } catch (e) {
          log.error "Squashed exception ${e.getClass().getName()} thrown by ${accountCredentials}."
          throw e
        }
      }

      spaceCache = tempSpaceCache
      serviceCache = tempServiceCache
      this.routeCache = tempRouteCache

      serverGroupsByAccount = tempServerGroupsByAccount
      serverGroupByAccountAndServerGroupName = tempServerGroupByAccountAndServerGroupName
      serverGroupsByAccountAndClusterName = tempServerGroupsByAccountAndClusterName

      clustersByApplicationName = tempClustersByApplicationName
      clustersByApplicationAndAccount = tempClustersByApplicationAndAccount
      clusterByAccountAndClusterName = tempClusterByAccountAndClusterName
      clustersByAccount = tempClustersByAccount

      services = tempServices
      servicesByAccount = tempServicesByAccount
      servicesByRegion = tempServicesByRegion

      applicationByName = tempApplicationByName

      this.loadBalancersByAccount = tempLoadBalancersByAccount
      loadBalancersByApplication = tempLoadBalancersByApplication
      loadBalancersByAccountAndClusterName = tempLoadBalancersByAccountAndClusterName

      this.instancesByAccountAndId = tempInstancesByAccountAndId
    } finally {
      cacheLock.unlock()
    }

    log.info "Finished loading CF resources."

  }

    /**
   * Update the status of a particular server group by connecting to CF.
   * @param data
   * @return able to get a lock or not?
   */
  void handleCacheUpdate(Map<String, ? extends Object> data) {
    log.info "${data.serverGroupName}: Refreshing cache in account $data.account"

    if (cacheLock.tryLock(60L, TimeUnit.SECONDS)) {
      log.info "${data.serverGroupName}: Acquired cacheLock for updating cache."

      try {
        def accountCredentials = accountCredentialsProvider.getCredentials(data.account)

        if (accountCredentials instanceof CloudFoundryAccountCredentials) {
          CloudFoundryAccountCredentials credentials = (CloudFoundryAccountCredentials) accountCredentials

          log.info "${data.serverGroupName}: Logging in to ${credentials.api} as ${credentials.name}"

          def client = new CloudFoundryClient(credentials.credentials, credentials.api.toURL(), true)

          Names names = Names.parseName(data.serverGroupName)

          try {
            log.debug "${data.serverGroupName}: Fetching Cloud Foundry application."

            CloudApplication app = client.getApplication(data.serverGroupName)

            log.debug "${data.serverGroupName}: Fetching details about ${credentials.space} space."

            CloudSpace space = client.getSpace(credentials.space)
            app.space = space

            log.debug "${data.serverGroupName}: Building server group."

            def serverGroup = lookupServerGroup(client, credentials, app, names, space, serviceCache, routeCache,
                serverGroupsByAccount,
                serverGroupByAccountAndServerGroupName,
                serverGroupsByAccountAndClusterName,
                loadBalancersByAccount, loadBalancersByApplication, loadBalancersByAccountAndClusterName, instancesByAccountAndId)

            /**
             * Update cluster maps
             */

            clusterByAccountAndClusterName[credentials.name][names.cluster].serverGroups.removeAll {it.name == data.serverGroupName}
            clusterByAccountAndClusterName[credentials.name][names.cluster].serverGroups.add(serverGroup)

            clustersByAccount[credentials.name].serverGroups.removeAll {it.name == data.serverGroupName}
            clustersByAccount[credentials.name].serverGroups.add(serverGroup)

            clustersByApplicationAndAccount[names.app][credentials.name].serverGroups.removeAll {it.name == data.serverGroupName}
            clustersByApplicationAndAccount[names.app][credentials.name].serverGroups.add(serverGroup)

            clustersByApplicationName[names.app].serverGroups.removeAll {it.name == data.serverGroupName}
            clustersByApplicationName[names.app].serverGroups.add(serverGroup)

          } catch (CloudFoundryException e) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
              log.info "${data.serverGroupName} doesn't exist. Wiping from the caches."

              serverGroupByAccountAndServerGroupName[credentials.name].remove(data.serverGroupName)
              serverGroupsByAccount[credentials.name].removeAll {it.name == data.serverGroupName}
              serverGroupsByAccountAndClusterName[credentials.name][names.cluster].removeAll {it.name == data.serverGroupName}

              clusterByAccountAndClusterName[credentials.name][names.cluster].serverGroups.removeAll {it.name == data.serverGroupName}
              clustersByAccount[credentials.name].serverGroups.removeAll {it.name == data.serverGroupName}
              clustersByApplicationAndAccount[names.app][credentials.name].serverGroups.removeAll {it.name == data.serverGroupName}
              clustersByApplicationName[names.app].serverGroups.removeAll {it.name == data.serverGroupName}

            }
          }

          log.info "${data.serverGroupName}: Finished refreshing cache in ${data.account} account."

        }
      } finally {
        cacheLock.unlock()
      }
    } else {
      log.info "${data.serverGroupName}: Unable to acquire cacheLock for updating cache."
    }

  }

  /**
   * Create a server group based on details from Cloud Foundry
   *
   * @param app
   * @param credentials
   * @param space
   * @return
   */
  private CloudFoundryServerGroup lookupServerGroup(CloudFoundryClient client, CloudFoundryAccountCredentials credentials,
                                                    CloudApplication app, Names names, CloudSpace space,
                                                    Map<String, Set<CloudService>> serviceCache,
                                                    Set<CloudRoute> routeCache,
                                                    Map<String, Set<CloudFoundryServerGroup>> serverGroupsByAccount,
                                                    Map<String, Map<String, CloudFoundryServerGroup>> serverGroupByAccountAndServerGroupName,
                                                    Map<String, Map<String, Set<CloudFoundryServerGroup>>> serverGroupsByAccountAndClusterName,
                                                    Map<String, Set<CloudFoundryLoadBalancer>> loadBalancersByAccount,
                                                    Map<String, Set<CloudFoundryLoadBalancer>> loadBalancersByApplication,
                                                    Map<String, Map<String, Set<CloudFoundryLoadBalancer>>> loadBalancersByAccountAndClusterName,
                                                    Map<String, Map<String, CloudFoundryApplicationInstance>> instancesByAccountAndId) {

    log.debug "${app.name}: Capturing core attributes of server group (default DISABLED)."

    def serverGroup = new CloudFoundryServerGroup([
        name              : app.name,
        nativeApplication : app,
        envVariables      : app.envAsMap,
        buildInfo         : [
            commit: app.envAsMap[CloudFoundryConstants.COMMIT_HASH],
            branch: app.envAsMap[CloudFoundryConstants.COMMIT_BRANCH],
            package_name: app.envAsMap[CloudFoundryConstants.PACKAGE],
            jenkins: [
                fullUrl: app.envAsMap[CloudFoundryConstants.JENKINS_HOST],
                name: app.envAsMap[CloudFoundryConstants.JENKINS_NAME],
                number: app.envAsMap[CloudFoundryConstants.JENKINS_BUILD]
            ]
        ],
        consoleLink : "${credentials.console}/organizations/${space.organization.meta.guid}/spaces/${space.meta.guid}/applications/${app.meta.guid}",
        logsLink : "${credentials.console}/organizations/${space.organization.meta.guid}/spaces/${space.meta.guid}/applications/${app.meta.guid}/tailing_logs"
    ])

    lookupServices(serverGroup, credentials, app, space, names, serviceCache)

    lookupLoadBalancers(client, credentials, app, space, names, serverGroup, routeCache,
        loadBalancersByAccount, loadBalancersByApplication, loadBalancersByAccountAndClusterName, instancesByAccountAndId)

    serverGroupByAccountAndServerGroupName[credentials.name][serverGroup.name] = serverGroup

    serverGroupsByAccount[credentials.name].removeAll {it.name == serverGroup.name}
    serverGroupsByAccount[credentials.name].add(serverGroup)

    serverGroupsByAccountAndClusterName[credentials.name][names.cluster].removeAll {it.name == serverGroup.name}
    serverGroupsByAccountAndClusterName[credentials.name][names.cluster].add(serverGroup)

    serverGroup
  }

  private void lookupServices(CloudFoundryServerGroup serverGroup, CloudFoundryAccountCredentials credentials,
                              CloudApplication app, CloudSpace space, Names names,
                              Map<String, Set<CloudService>> serviceCache) {

    log.debug "${serverGroup.name}: Looking up services."

    serverGroup.services.addAll(serviceCache[space.meta.guid].findAll {app.services.contains(it.name)}.collect {
      log.debug "${serverGroup.name}: Bound to ${it.name} service."
      new CloudFoundryService([
        type: 'cf',
        id: it.meta.guid,
        name: it.name,
        application: names.app,
        accountName: credentials.name,
        region: space.organization.name,
        nativeService: it
    ])})
  }

  /**
   * Look up details about a server group's load balancers and assign them to the server group.
   *
   * @param client
   * @param credentials
   * @param app
   * @param space
   * @param serverGroup
   * @param routeCache
   * @param loadBalancersByAccount
   * @param instancesByAccountAndId
   */
  private void lookupLoadBalancers(CloudFoundryClient client, CloudFoundryAccountCredentials credentials,
                                   CloudApplication app, CloudSpace space, Names names,
                                   CloudFoundryServerGroup serverGroup,
                                   Set<CloudRoute> routeCache,
                                   Map<String, Set<CloudFoundryLoadBalancer>> loadBalancersByAccount,
                                   Map<String, Set<CloudFoundryLoadBalancer>> loadBalancersByApplication,
                                   Map<String, Map<String, Set<CloudFoundryLoadBalancer>>> loadBalancersByAccountAndClusterName,
                                   Map<String, Map<String, CloudFoundryApplicationInstance>> instancesByAccountAndId) {
    try {

      log.debug "${serverGroup.name}: Looking up load balancers."

      def loadBalancers = app.envAsMap[CloudFoundryConstants.LOAD_BALANCERS]?.split(',').collect { route ->

        log.debug "${serverGroup.name}: Associated with ${route} load balancer."

        def loadBalancer = loadBalancersByAccount[credentials.name].find { it.name == route }

        if (loadBalancer == null) {
          log.debug "${serverGroup.name}: Creating new internal copy of ${route} load balancer."
          loadBalancer = new CloudFoundryLoadBalancer([
              name       : route,
              region     : space.organization.name,
              account    : credentials.name,
              nativeRoute: routeCache.find { it.host == route }
          ])
          loadBalancersByAccount[credentials.name].add(loadBalancer)
        }

        if (app.uris?.find { it == loadBalancer.nativeRoute.toString()}) {
          log.debug "${serverGroup.name}: Mapped to ${route} load balancer. Flagging as ENABLED."
          serverGroup.disabled = false
        }

        lookupInstances(client, credentials, app, space, serverGroup, instancesByAccountAndId)

        def serverGroupSummary = [
            name      :        serverGroup.name,
            isDisabled:        serverGroup.isDisabled(),
            instances :        serverGroup.instances,
            detachedInstances: [] as Set
        ]

        loadBalancer.serverGroups.removeAll {it.name == serverGroup.name}
        loadBalancer.serverGroups.add(serverGroupSummary)

        return loadBalancer

      }.findAll {it != null}
      serverGroup.nativeLoadBalancers = loadBalancers != null ? loadBalancers : [] as Set<CloudFoundryLoadBalancer>

      serverGroup.nativeLoadBalancers.each { loadBalancer ->
        loadBalancersByAccountAndClusterName[credentials.name][names.cluster].removeAll {it.name == loadBalancer.name}
        loadBalancersByAccountAndClusterName[credentials.name][names.cluster].add(loadBalancer)

        loadBalancersByApplication[names.app].removeAll {it.name == loadBalancer.name}
        loadBalancersByApplication[names.app].add(loadBalancer)
      }

    } catch (HttpServerErrorException e) {
      log.warn "${serverGroup.name}: Unable to retrieve routes in ${credentials.name} -> ${e.message}"
    }
  }

  /**
   * Look up the details about a server group's instances and update the server group accordingly.
   *
   * @param client
   * @param credentials
   * @param app
   * @param space
   * @param serverGroup
   * @param instancesByAccountAndId
   */
  private void lookupInstances(CloudFoundryClient client, CloudFoundryAccountCredentials credentials,
                               CloudApplication app, CloudSpace space,
                               CloudFoundryServerGroup serverGroup,
                               Map<String, Map<String, CloudFoundryApplicationInstance>> instancesByAccountAndId) {

    log.debug "${serverGroup.name}: Looking up instances."
    try {
      serverGroup.instances = client.getApplicationInstances(app)?.instances.collect {
        log.debug "${serverGroup.name}: Found instance ${it.index} with properties ${it.properties}"
        def instance = new CloudFoundryApplicationInstance([
            name             : "${app.name}:${it.index}",
            nativeApplication: app,
            nativeInstance   : it,
            logsLink         : "${credentials.console}/organizations/${space.organization.meta.guid}/spaces/${space.meta.guid}/applications/${app.meta.guid}/tailing_logs"
        ])
        instance.healthState = instanceStateToHealthState(it.state).toString()
        instance.health = createInstanceHealth(instance)

        if (instancesByAccountAndId[credentials.name][instance.name]?.healthState != instance.healthState) {
          log.info "${serverGroup.name}: Updating ${credentials.name}/${instance.name} to ${instance.healthState}"
        } else {
          log.debug "${serverGroup.name}: ${credentials.name}/${instance.name} is already at ${instance.healthState}"
        }
        instancesByAccountAndId[credentials.name][instance.name] = instance
        instance
      } as Set<CloudFoundryApplicationInstance>
    } catch (HttpServerErrorException e) {
      log.warn "${serverGroup.name}: Unable to retrieve instance data about ${serverGroup.name} in ${credentials.name} => ${e.message}"
    }
  }

  private ArrayList<LinkedHashMap<String, String>> createInstanceHealth(CloudFoundryApplicationInstance instance) {
    log.debug "${instance.nativeApplication.name}: Creating instance health object for ${instance.name}"

    [
      [
         state      : instance.healthState.toString(),
         zone       : instance.zone,
         type       : 'serverGroup',
         description: 'Is this CF server group running?'
      ]
    ]
  }

  /**
   * Convert from {@link InstanceState} to {@link HealthState}.
   *
   * @param instanceState
   * @return
   */
  private HealthState instanceStateToHealthState(InstanceState instanceState) {
    switch (instanceState) {
      case InstanceState.DOWN:
        return HealthState.Down
      case InstanceState.STARTING:
        return HealthState.Starting
      case InstanceState.RUNNING:
        return HealthState.Up
      case InstanceState.CRASHED:
        return HealthState.Down
      case InstanceState.FLAPPING:
        return HealthState.Unknown
      case InstanceState.UNKNOWN:
        return HealthState.Unknown
    }
  }

}
