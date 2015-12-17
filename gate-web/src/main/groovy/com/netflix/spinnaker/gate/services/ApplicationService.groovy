/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.config.Service
import com.netflix.spinnaker.gate.config.ServiceConfiguration
import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.converter.ConversionException
import rx.Observable
import rx.Scheduler
import rx.schedulers.Schedulers

import javax.annotation.PostConstruct
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@CompileStatic
@Component
@Slf4j
class ApplicationService {
  private static final String GROUP = "applications"

  private Scheduler scheduler = Schedulers.io()

  @Autowired
  ServiceConfiguration serviceConfiguration

  @Autowired
  ClouddriverService clouddriverService

  @Autowired
  Front50Service front50Service

  @Autowired
  ExecutorService executorService

  private AtomicReference<List<Map>> allApplicationsCache = new AtomicReference<>([])

  @PostConstruct
  void startMonitoring() {
    Observable
      .timer(60000, TimeUnit.MILLISECONDS, scheduler)
      .repeat()
      .subscribe({ Long interval ->
      try {
        log.info("Refreshing Application List")
        allApplicationsCache.set(tick(true))
        log.info("Refreshed Application List")
      } catch (e) {
        log.error("Unable to refresh application list, reason: ${e.message}")
      }
    })
  }

  /**
   * Fetching cluster details is a potentially expensive call to cloud driver, but allows us to provide a definitive
   * list of accounts that an application has a presence in.
   *
   * As a trade-off, we'll fetch cluster details on the background refresh loop and merge in the account details
   * when applications are requested on-demand.
   *
   * @param expandClusterNames Should cluster details (for each application) be fetched from cloud driver
   * @return Applications
   */
  List<Map<String, Object>> tick(boolean expandClusterNames = true) {
    def applicationListRetrievers = buildApplicationListRetrievers(expandClusterNames)
    List<Future<List<Map>>> futures = executorService.invokeAll(applicationListRetrievers)
    List<List<Map>> all = futures.collect { it.get() }
    List<Map> flat = (List<Map>) all?.flatten()?.toList()
    return mergeApps(flat, serviceConfiguration.getService('front50')).collect {
      it.attributes
    } as List<Map>
  }

  List<Map> getAll() {
    try {
      def applicationsByName = allApplicationsCache.get().groupBy { it.name }
      return tick(false).collect { Map application ->
        applicationsByName[application.name.toString()]?.each { Map cacheApplication ->
          application.accounts = mergeAccounts(application.accounts as String, cacheApplication.accounts as String)
        }
        application
      } as List<Map>
    } catch (e) {
      log.error("Unable to fetch all applications, returning most recently cached version", e)
    }
    return allApplicationsCache.get()
  }

  Map get(String name) {
    def applicationRetrievers = buildApplicationRetrievers(name)
    def futures = executorService.invokeAll(applicationRetrievers)
    List<Map> applications = (List<Map>) futures.collect { it.get() }

    List<Map> mergedApps = mergeApps(applications, serviceConfiguration.getService('front50'))
    return mergedApps ? mergedApps[0] : null
  }

  List<Map> getPipelineConfigs(String app) {
    if (!front50Service) {
      return []
    }

    HystrixFactory.newListCommand(GROUP, "getPipelineConfigsForApplication") {
      front50Service.getPipelineConfigs(app)
    } execute()
  }

  Map getPipelineConfig(String app, String pipelineName) {
    if (!front50Service) {
      return null
    }
    HystrixFactory.newMapCommand(GROUP, "getPipelineConfigForApplicationAndPipeline") {
      front50Service.getPipelineConfigs(app).find { it.name == pipelineName }
    } execute()
  }

  List<Map> getStrategyConfigs(String app) {
    if (!front50Service) {
      return []
    }

    HystrixFactory.newListCommand(GROUP, "getStrategyConfigForApplication") {
      front50Service.getStrategyConfigs(app)
    } execute()
  }

  private Collection<Callable<List<Map>>> buildApplicationListRetrievers(boolean expandClusterNames) {
    def clouddriverApplicationsRetriever = new ClouddriverApplicationsRetriever(
      clouddriverService,
      allApplicationsCache,
      expandClusterNames
    )
    def globalAccounts = fetchGlobalAccounts()

    if (globalAccounts) {
      return globalAccounts.collectMany { String globalAccount ->
        [new ApplicationListRetriever(globalAccount, front50Service, allApplicationsCache), clouddriverApplicationsRetriever]
      } as Collection<Callable<List<Map>>>
    }

    return [clouddriverApplicationsRetriever]
  }

  private Collection<Callable<Map>> buildApplicationRetrievers(String applicationName) {
    def clouddriverApplicationRetriever = new ClouddriverApplicationRetriever(applicationName, clouddriverService)
    def globalAccounts = fetchGlobalAccounts()

    if (globalAccounts) {
      return globalAccounts.collectMany { String globalAccount ->
        [
          new Front50ApplicationRetriever(globalAccount, applicationName, front50Service, allApplicationsCache),
          clouddriverApplicationRetriever
        ]
      } as Collection<Callable<Map>>
    }

    return [clouddriverApplicationRetriever]
  }

  private Collection<String> fetchGlobalAccounts() {
    HystrixFactory.newListCommand(GROUP, "fetchGlobalAccounts", {
      front50Service.credentials.findAll { it.global == true }.collect { it.name }
    }, { [] }).execute()
  }

  @CompileDynamic
  private static List<Map> mergeApps(List<Map<String, Object>> applications, Service applicationServiceConfig) {

    try {
      Map<String, Map<String, Object>> merged = [:]
      for (Map<String, Object> app in applications) {
        if (!app || !app.name) continue
        String key = (app.name as String)?.toLowerCase()
        if (key && !merged.containsKey(key)) {
          merged[key] = [name: key, attributes: [:], clusters: [:]] as Map<String, Object>
        }
        Map mergedApp = (Map) merged[key]
        if (app.containsKey("clusters") || app.containsKey("clusterNames")) {
          // Clouddriver
          if (app.clusters) {
            (mergedApp.clusters as Map).putAll(app.clusters as Map)
          }
          String accounts = (app.clusters as Map)?.keySet()?.join(',') ?:
            (app.clusterNames as Map)?.keySet()?.join(',')

          mergedApp.attributes.accounts = mergeAccounts(accounts, mergedApp.attributes.accounts)

          (app["attributes"] as Map).entrySet().each {
            if (it.value && !(mergedApp.attributes as Map)[it.key]) {
              // don't overwrite existing attributes with metadata from clouddriver
              (mergedApp.attributes as Map)[it.key] = it.value
            }
          }
        } else {
          Map attributes = app.attributes ?: app
          attributes.entrySet().each {
            if (it.key == 'accounts') {
              mergedApp.attributes.accounts = mergeAccounts(mergedApp.attributes.accounts, it.value)
            } else if (it.value) {
              (mergedApp.attributes as Map)[it.key] = it.value
            }
          }
        }

        // ensure that names are consistently lower-cased.
        mergedApp.name = key.toLowerCase()
        mergedApp.attributes['name'] = mergedApp.name
      }

      Set<String> applicationFilter = applicationServiceConfig.config?.includedAccounts?.split(',')?.toList()?.findResults {
        it.trim() ?: null
      } ?: null
      return merged.values().toList().findAll { Map<String, Object> account ->
        if (applicationFilter == null) {
          return true
        }
        String[] accounts = account?.attributes?.accounts?.split(',')
        if (accounts == null) {
          return true
        }
        return accounts.any { applicationFilter.contains(it) }
      }
    } catch (Throwable t) {
      t.printStackTrace()
      throw t
    }
  }

  static String mergeAccounts(String accounts1, String accounts2) {
    return [accounts1, accounts2].collect { String s ->
      s?.split(',')?.toList()?.findResults { it.trim() ?: null } ?: []
    }.flatten().toSet().sort().join(',')
  }

  static class ApplicationListRetriever implements Callable<List<Map>> {
    private final String account
    private final Front50Service front50
    private final AtomicReference<List<Map>> allApplicationsCache
    private final Object principal

    ApplicationListRetriever(String account, Front50Service front50, AtomicReference<List<Map>> allApplicationsCache) {
      this.account = account
      this.front50 = front50
      this.allApplicationsCache = allApplicationsCache
      this.principal = SecurityContextHolder.context?.authentication?.principal
    }

    @Override
    List<Map> call() throws Exception {
      HystrixFactory.newListCommand(GROUP, "getApplicationsFromFront50", {
        AuthenticatedRequest.propagate({
          try {
            def apps = front50.getAll(account)
            return apps.collect {
              if (!it.accounts) {
                it.accounts = account
              }
              it
            }
          } catch (RetrofitError e) {
            if (e.response?.status == 404) {
              return []
            } else {
              throw e
            }
          }
        }, false, principal).call() as List<Map>
      }, {
        return allApplicationsCache.get()
      }).execute()
    }
  }

  static class Front50ApplicationRetriever implements Callable<Map> {
    private final String account
    private final String name
    private final Front50Service front50
    private final AtomicReference<List<Map>> allApplicationsCache
    private final Object principal

    Front50ApplicationRetriever(String account,
                                String name,
                                Front50Service front50,
                                AtomicReference<List<Map>> allApplicationsCache) {
      this.account = account
      this.name = name
      this.front50 = front50
      this.allApplicationsCache = allApplicationsCache
      this.principal = SecurityContextHolder.context?.authentication?.principal
    }

    @Override
    Map call() throws Exception {
      HystrixFactory.newMapCommand(GROUP, "getApplicationFromFront50", {
        AuthenticatedRequest.propagate({
          try {
            def metadata = front50.getMetaData(account, name)
            if (metadata && !metadata.accounts) {
              metadata.accounts = account
            }
            metadata ?: [:]
          } catch (ConversionException ignored) {
            return [:]
          } catch (RetrofitError e) {
            if ((e.cause instanceof ConversionException) || e.response.status == 404) {
              return [:]
            } else {
              throw e
            }
          }
        }, false, principal).call() as Map
      }, {
        allApplicationsCache.get().find { name.equalsIgnoreCase(it.name as String) }
      }).execute()
    }
  }

  static class ClouddriverApplicationsRetriever implements Callable<List<Map>> {
    private final ClouddriverService clouddriver
    private final Object principal
    private final AtomicReference<List<Map>> allApplicationsCache
    private final boolean expandClusterNames

    ClouddriverApplicationsRetriever(ClouddriverService clouddriver,
                                     AtomicReference<List<Map>> allApplicationsCache,
                                     boolean expandClusterNames) {
      this.clouddriver = clouddriver
      this.allApplicationsCache = allApplicationsCache
      this.expandClusterNames = expandClusterNames
      this.principal = SecurityContextHolder.context?.authentication?.principal
    }

    @Override
    List<Map> call() throws Exception {
      HystrixFactory.newListCommand(GROUP, "getApplicationsFromCloudDriver", {
        AuthenticatedRequest.propagate({
          try {
            clouddriver.getApplications(expandClusterNames)
          } catch (RetrofitError e) {
            if (e.response?.status == 404) {
              return []
            } else {
              throw e
            }
          }
        }, false, principal).call() as List<Map>
      }, { return allApplicationsCache.get() }).execute()
    }
  }

  static class ClouddriverApplicationRetriever implements Callable<Map> {
    private final String name
    private final ClouddriverService clouddriver
    private final Object principal


    ClouddriverApplicationRetriever(String name, ClouddriverService clouddriver) {
      this.name = name
      this.clouddriver = clouddriver
      this.principal = SecurityContextHolder.context?.authentication?.principal
    }

    @Override
    Map call() throws Exception {
      HystrixFactory.newMapCommand(GROUP, "getApplicationFromCloudDriver", {
        AuthenticatedRequest.propagate({
          try {
            return clouddriver.getApplication(name)
          } catch (RetrofitError e) {
            if (e.response?.status == 404) {
              return [:]
            } else {
              throw e
            }
          }
        }, false, principal).call() as Map
      }, { [:] }).execute()
    }
  }
}
