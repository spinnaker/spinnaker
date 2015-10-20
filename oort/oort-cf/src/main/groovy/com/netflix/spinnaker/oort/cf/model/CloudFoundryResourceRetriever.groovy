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

import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConfigurationProperties
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.oort.model.HealthState
import groovy.util.logging.Slf4j
import org.cloudfoundry.client.lib.CloudFoundryClient
import org.cloudfoundry.client.lib.domain.*
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
  Map<String, Map<String, CloudFoundryServerGroup>> serverGroupsByAccountAndServerGroupName = [:].withDefault {[:]}
  Map<String, Map<String, Set<CloudFoundryServerGroup>>> serverGroupsByAccountAndClusterName =
      [:].withDefault {[:].withDefault {[] as Set<CloudFoundryServerGroup>}}

  Map<String, Set<CloudFoundryCluster>> clustersByApplicationName = [:].withDefault {[] as Set<CloudFoundryCluster>}
  Map<String, Map<String, Set<CloudFoundryCluster>>> clustersByApplicationAndAccount =
      [:].withDefault {[:].withDefault {[] as Set<CloudFoundryCluster>}}
  Map<String, Map<String, CloudFoundryCluster>> clustersByAccountAndClusterName =
      [:].withDefault {[:].withDefault {new CloudFoundryCluster()}}
  Map<String, Set<CloudFoundryCluster>> clustersByAccount = [:].withDefault {[] as Set<CloudFoundryCluster>}

  Map<String, Set<CloudFoundryService>> servicesByAccount = [:].withDefault {[] as Set<CloudFoundryService>}

  Map<String, CloudFoundryApplication> applicationByName = [:].withDefault {new CloudFoundryApplication()}

  Map<String, Set<CloudFoundryApplicationInstance>> instances = [:].withDefault {[] as Set<CloudFoundryApplicationInstance>}


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

      accountCredentialsProvider.all.each { accountCredentials ->
        try {
          if (accountCredentials instanceof CloudFoundryAccountCredentials) {
            log.info "Logging in to ${cloudFoundryConfigurationProperties.api} as ${accountCredentials.name}"

            CloudFoundryAccountCredentials credentials = (CloudFoundryAccountCredentials) accountCredentials

            def client = new CloudFoundryClient(credentials.credentials, cloudFoundryConfigurationProperties.api.toURL())

            /**
             * In spinnaker terms:
             * sagan is an app
             * sagan is a cluster in an app in a location spring.io/production
             * sagan-blue is a server group in the sagan cluster in the sagan app
             * sagan-blue:0, sagan-blue:1, etc. are instances inside the sagan-blue server group
             */

            log.info "Looking up spaces..."
            client.spaces.each { space ->
              if (!spaceCache.containsKey(space.meta.guid)) {
                spaceCache.put(space.meta.guid, space)
              }
            }

            log.info "Looking up services..."
            spaceCache.values().each { space ->
              def conn = new CloudFoundryClient(credentials.credentials, cloudFoundryConfigurationProperties.api.toURL(),
                  space)
              conn.services.each { service ->
                serviceCache.get(space.meta.guid).add(service)
              }
              conn.logout()
            }

            log.info "Look up all applications..."
            def cfApplications = client.applications

            cfApplications.each { app ->

              def space = spaceCache.get(app.space.meta.guid)
              app.space = space
              def account = space?.organization?.name + ':' + space?.name

              def serverGroup = new CloudFoundryServerGroup([
                  name: app.name,
                  nativeApplication: app,
                  services: app.services as Set,
                  nativeServices: app.services.collect { name -> name == serviceCache[space.meta.guid].name }
              ])

              serverGroupsByAccount[account].add(serverGroup)

              serverGroupsByAccountAndServerGroupName[account][app.name] = serverGroup

              def clusterName = clusterName(app.name)

              serverGroupsByAccountAndClusterName[account][clusterName].add(serverGroup)

              def cluster = clustersByAccountAndClusterName[account][clusterName]
              cluster.name = clusterName
              cluster.accountName = account
              cluster.serverGroups.add(serverGroup)
              cluster.services.addAll(serviceCache[space.meta.guid].findAll {serverGroup.services.contains(it.name)}
                  .collect {new CloudFoundryService([
                      name: it.name,
                      type: it.label,
                      nativeService: it
                  ])})

              cluster.services.each { service ->
                service.serverGroups.add(serverGroup.name)
              }

              clustersByApplicationName[cluster.name].add(cluster)

              clustersByApplicationAndAccount[cluster.name][account].add(cluster)

              clustersByAccount[account].add(cluster)
              servicesByAccount[account].addAll(cluster.services)

              def application = applicationByName[cluster.name]
              application.name = cluster.name
              application.applicationClusters[account].add(cluster)
              application.clusterNames[account].add(cluster.name)

              try {
                serverGroup.instances = client.getApplicationInstances(app)?.instances.collect {
                  new CloudFoundryApplicationInstance([
                      name             : "${app.name}:${it.index}",
                      healthState      : instanceStateToHealthState(it.state),
                      nativeApplication: app,
                      nativeInstance:   it
                  ])
                } as Set<CloudFoundryApplicationInstance>

                instances[account].addAll(serverGroup.instances)
              } catch (HttpServerErrorException e) {
                log.warn "Unable to retrieve instance data about ${app.name} in ${account} => ${e.message}"
              }

            }

            log.info "Done loading new version of data"

          }
        } catch (e) {
          log.error "Squashed exception ${e.getClass().getName()} thrown by ${accountCredentials}."
        }
      }

    } finally {
      cacheLock.unlock()
    }

    log.info "Finished loading CF resources."

  }

  String clusterName(String serverGroupName) {
    def variants = ['-blue', '-green']

    for (String variant : variants) {
      if (serverGroupName.endsWith(variant)) {
        return serverGroupName - variant
      }
    }

    serverGroupName
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
