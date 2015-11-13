/*
 * Copyright 2015 The original authors.
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
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.oort.model.HealthState
import groovy.util.logging.Slf4j
import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.domain.CloudService
import org.cloudfoundry.client.lib.domain.CloudSpace
import org.cloudfoundry.client.lib.domain.InstanceState
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpServerErrorException

import javax.annotation.PostConstruct
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
/**
 * @author Greg Turnquist
 */
@Slf4j
class CloudFoundryResourceRetriever {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  CloudFoundryConfigurationProperties cloudFoundryConfigurationProperties

  protected Lock cacheLock = new ReentrantLock()

  Map<String, CloudSpace> spaceCache = new HashMap<>()
  Map<String, Set<CloudService>> serviceCache = [:].withDefault { [] as Set<CloudService> }

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

      Map<String, Map<String, CloudFoundryApplicationInstance>> tempInstancesByAccountAndId =
          [:].withDefault {[:] as Map<String, CloudFoundryApplicationInstance>}

      accountCredentialsProvider.all.each { accountCredentials ->
        try {
          if (accountCredentials instanceof CloudFoundryAccountCredentials) {
            CloudFoundryAccountCredentials credentials = (CloudFoundryAccountCredentials) accountCredentials

            log.info "Logging in to ${credentials.api} as ${credentials.name}"

            def client = new CloudFoundryClient(credentials.credentials, credentials.api.toURL(), true)

            /**
             * In spinnaker terms:
             * sagan is an app
             * sagan is a cluster in an app in a location spring.io/production
             * sagan-blue is a server group in the sagan cluster in the sagan app
             * sagan-blue:0, sagan-blue:1, etc. are instances inside the sagan-blue server group
             */

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

            log.info "Look up all applications..."
            def cfApplications = client.applications

            cfApplications.each { app ->

              app.space = space
              def account = credentials.name

              Names names = Names.parseName(app.name)

              def serverGroup = new CloudFoundryServerGroup([
                  name: app.name,
                  nativeApplication: app,
                  envVariables: app.envAsMap
              ])

              serverGroup.services.addAll(tempServiceCache[space.meta.guid].findAll {app.services.contains(it.name)}
                .collect {new CloudFoundryService([
                  type: 'cf',
                  id: it.meta.guid,
                  name: it.name,
                  application: names.app,
                  accountName: account,
                  region: space.organization.name,
                  nativeService: it
                ])})

              try {
                serverGroup.instances = client.getApplicationInstances(app)?.instances.collect {
                  def healthState = instanceStateToHealthState(it.state)
                  def health = [[
                          state      : healthState.toString(),
                          type       : 'serverGroup',
                          description: 'State of the CF server group'
                  ]]

                  def instance = new CloudFoundryApplicationInstance([
                          name             : "${app.name}:${it.index}",
                          healthState      : healthState,
                          health           : health,
                          nativeApplication: app,
                          nativeInstance:   it,
                          logsLink         : "${credentials.console}/organizations/${space.organization.meta.guid}/spaces/${space.meta.guid}/applications/${app.meta.guid}/tailing_logs"
                  ])
                  if (tempInstancesByAccountAndId[account][instance.name]?.healthState != instance.healthState) {
                    log.info "Updating ${account}/${instance.name} to ${instance.healthState}"
                  }
                  tempInstancesByAccountAndId[account][instance.name] = instance
                  instance
                } as Set<CloudFoundryApplicationInstance>
              } catch (HttpServerErrorException e) {
                log.warn "Unable to retrieve instance data about ${app.name} in ${account} => ${e.message}"
              }

              tempServices.addAll(serverGroup.services)
              tempServicesByAccount[account].addAll(serverGroup.services)
              tempServicesByRegion[space.organization.name].addAll(serverGroup.services)

              if (tempServerGroupsByAccount[account].contains(serverGroup)) {
                tempServerGroupsByAccount[account].remove(serverGroup)
              }
              tempServerGroupsByAccount[account].add(serverGroup)

              tempServerGroupByAccountAndServerGroupName[account][app.name] = serverGroup

              def clusterName = names.cluster

              if (tempServerGroupsByAccountAndClusterName[account][clusterName].contains(serverGroup)) {
                tempServerGroupsByAccountAndClusterName[account][clusterName].remove(serverGroup)
              }
              tempServerGroupsByAccountAndClusterName[account][clusterName].add(serverGroup)

              def cluster = tempClusterByAccountAndClusterName[account][clusterName]
              cluster.name = clusterName
              cluster.accountName = account
              if (cluster.serverGroups.contains(serverGroup)) {
                cluster.serverGroups.remove(serverGroup)
              }
              cluster.serverGroups.add(serverGroup)

              tempClustersByApplicationName[names.app].add(cluster)

              tempClustersByApplicationAndAccount[names.app][account].add(cluster)

              tempClustersByAccount[account].add(cluster)

              def application = tempApplicationByName[names.app]
              application.name = names.app
              application.applicationClusters[account].add(cluster)
              application.clusterNames[account].add(cluster.name)
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

      instancesByAccountAndId = tempInstancesByAccountAndId
    } finally {
      cacheLock.unlock()
    }

    log.info "Finished loading CF resources."

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
