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
import com.netflix.spinnaker.gate.services.internal.OortService
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
  OortService oortService

  @Autowired
  Front50Service front50Service

  @Autowired
  ExecutorService executorService

  private AtomicReference<List<Map>> allApplicationsCache = new AtomicReference<>([])

  ApplicationService() {
    Observable
      .timer(30000, TimeUnit.MILLISECONDS, scheduler)
      .repeat()
      .subscribe({ Long interval -> tick() })
  }

  void tick() {
    log.info("Refreshing Application List")

    def applicationListRetrievers = buildApplicationListRetrievers()
    def applications = HystrixFactory.newListCommand(GROUP, "getAll", {
      List<Future<List<Map>>> futures = executorService.invokeAll(applicationListRetrievers)
      List<List<Map>> all = futures.collect { it.get() } // spread operator doesn't work here; no clue why.
      List<Map> flat = (List<Map>) all?.flatten()?.toList()
      def mergedApplications = mergeApps(flat, serviceConfiguration.getService('front50')).collect {
        it.attributes
      } as List<Map>

      return mergedApplications
    }, { return allApplicationsCache.get() }).execute()

    allApplicationsCache.set(applications)
    log.info("Refreshed Application List")
  }

  List<Map> getAll() {
    return allApplicationsCache.get()
  }

  Map get(String name) {
    def applicationRetrievers = buildApplicationRetrievers(name)

    HystrixFactory.newMapCommand(GROUP, "getAppByName", {
      try {
        def futures = executorService.invokeAll(applicationRetrievers)
        List<Map> applications = (List<Map>) futures.collect { it.get() }

        List<Map> mergedApps = mergeApps(applications, serviceConfiguration.getService('front50'))
        return mergedApps ? mergedApps[0] : null
      } catch (Exception e) {
        log.error("Unable to retrieve application '${name}'", e)
        throw e
      }
    }, { allApplicationsCache.get().find { it.name.toString().equalsIgnoreCase(name) } }).execute()
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

  Map getStrategyConfig(String app, String strategyName) {
    if (!front50Service) {
      return null
    }
    HystrixFactory.newMapCommand(GROUP, "getStrategyConfigForApplicationAndStrategy") {
      front50Service.getStrategyConfigs(app).find { it.name == strategyName }
    } execute()
  }

  private Collection<Callable<List<Map>>> buildApplicationListRetrievers() {
    def globalAccounts = front50Service.credentials.findAll { it.global == true }.collect { it.name } as List<String>
    return globalAccounts.collectMany { String globalAccount ->
      [new ApplicationListRetriever(globalAccount, front50Service), new OortApplicationsRetriever(oortService)]
    } as Collection<Callable<List<Map>>>
  }

  private Collection<Callable<Map>> buildApplicationRetrievers(String applicationName) {
    def globalAccounts = front50Service.credentials.findAll { it.global == true }.collect { it.name } as List<String>
    return globalAccounts.collectMany { String globalAccount ->
      [
        new Front50ApplicationRetriever(globalAccount, applicationName, front50Service),
        new OortApplicationRetriever(applicationName, oortService)
      ]
    } as Collection<Callable<Map>>
  }

  @CompileDynamic
  private static List<Map> mergeApps(List<Map<String, Object>> applications, Service applicationServiceConfig) {

    try {
      Closure<String> mergeAccounts = { String s1, String s2 ->
        [s1, s2].collect { String s ->
          s?.split(',')?.toList()?.findResults { it.trim() ?: null } ?: []
        }.flatten().toSet().sort().join(',')
      }
      Map<String, Map<String, Object>> merged = [:]
      for (Map<String, Object> app in applications) {
        if (!app || !app.name) continue
        String key = (app.name as String)?.toLowerCase()
        if (key && !merged.containsKey(key)) {
          merged[key] = [name: key, attributes: [:], clusters: [:]] as Map<String, Object>
        }
        Map mergedApp = (Map) merged[key]
        if (app.containsKey("clusters") || app.containsKey("clusterNames")) {
          // Oort
          if (app.clusters) {
            (mergedApp.clusters as Map).putAll(app.clusters as Map)
          }
          String accounts = (app.clusters as Map)?.keySet()?.join(',') ?:
            (app.clusterNames as Map)?.keySet()?.join(',')

          mergedApp.attributes.accounts = mergeAccounts(accounts, mergedApp.attributes.accounts)

          (app["attributes"] as Map).entrySet().each {
            if (it.value && !(mergedApp.attributes as Map)[it.key]) {
              // don't overwrite existing attributes with metadata from oort
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

  static class ApplicationListRetriever implements Callable<List<Map>> {
    private final String account
    private final Front50Service front50
    private final Object principal

    ApplicationListRetriever(String account, Front50Service front50) {
      this.account = account
      this.front50 = front50
      this.principal = SecurityContextHolder.context?.authentication?.principal
    }

    @Override
    List<Map> call() throws Exception {
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
          if (e.response.status == 404) {
            return []
          } else {
            throw e
          }
        }
      }, false, principal).call() as List<Map>
    }
  }

  static class Front50ApplicationRetriever implements Callable<Map> {
    private final String account
    private final String name
    private final Front50Service front50
    private final Object principal

    Front50ApplicationRetriever(String account, String name, Front50Service front50) {
      this.account = account
      this.name = name
      this.front50 = front50
      this.principal = SecurityContextHolder.context?.authentication?.principal
    }

    @Override
    Map call() throws Exception {
      AuthenticatedRequest.propagate({
        try {
          def metadata = front50.getMetaData(account, name)
          if (metadata && !metadata.accounts) {
            metadata.accounts = account
          }
          metadata ?: [:]
        } catch (ConversionException e) {
          return [:]
        } catch (RetrofitError e) {
          if ((e.cause instanceof ConversionException) || e.response.status == 404) {
            return [:]
          } else {
            throw e
          }
        }
      }, false, principal).call() as Map
    }
  }

  static class OortApplicationsRetriever implements Callable<List<Map>> {
    private final OortService oort
    private final Object principal

    OortApplicationsRetriever(OortService oort) {
      this.oort = oort
      this.principal = SecurityContextHolder.context?.authentication?.principal
    }

    @Override
    List<Map> call() throws Exception {
      AuthenticatedRequest.propagate({
        try {
          oort.applications
        } catch (RetrofitError e) {
          if (e.response.status == 404) {
            return []
          } else {
            throw e
          }
        }
      }, false, principal).call() as List<Map>
    }
  }

  static class OortApplicationRetriever implements Callable<Map> {
    private final String name
    private final OortService oort
    private final Object principal


    OortApplicationRetriever(String name, OortService oort) {
      this.name = name
      this.oort = oort
      this.principal = SecurityContextHolder.context?.authentication?.principal
    }

    @Override
    Map call() throws Exception {
      AuthenticatedRequest.propagate({
        try {
          return oort.getApplication(name)
        } catch (RetrofitError e) {
          if (e.response?.status == 404) {
            return [:]
          } else {
            throw e
          }
        }
      }, false, principal).call() as Map
    }
  }
}
