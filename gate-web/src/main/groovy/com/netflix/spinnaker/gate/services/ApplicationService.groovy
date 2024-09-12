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

import com.netflix.spinnaker.gate.config.ApplicationConfigurationProperties
import com.netflix.spinnaker.gate.config.Service
import com.netflix.spinnaker.gate.config.ServiceConfiguration
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.converter.ConversionException

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Collectors

@CompileStatic
@Component
@Slf4j
class ApplicationService {
  private ServiceConfiguration serviceConfiguration
  private ClouddriverServiceSelector clouddriverServiceSelector
  private Front50Service front50Service
  private ExecutorService executorService

  private AtomicReference<List<Map>> allApplicationsCache
  private ApplicationConfigurationProperties applicationConfigurationProperties

  @Autowired
  ApplicationService(
    ServiceConfiguration serviceConfiguration,
    ClouddriverServiceSelector clouddriverServiceSelector,
    Front50Service front50Service,
    ApplicationConfigurationProperties applicationConfigurationProperties
  ){
    this.serviceConfiguration = serviceConfiguration
    this.clouddriverServiceSelector = clouddriverServiceSelector
    this.front50Service = front50Service
    this.applicationConfigurationProperties = applicationConfigurationProperties
    this.executorService = Executors.newCachedThreadPool()
    this.allApplicationsCache = new AtomicReference<>([])
  }

  // used in tests
  AtomicReference<List<Map>> getAllApplicationsCache(){
    this.allApplicationsCache
  }

  @Scheduled(fixedDelayString = '${services.front50.applicationRefreshIntervalMs:5000}')
  void refreshApplicationsCache() {
    try {
      allApplicationsCache.set(tick(true))
      log.debug("Refreshed Application List (applications: {})", allApplicationsCache.get().size())
    } catch (e) {
      log.error("Unable to refresh application list", e)
    }
  }

  /**
   * Fetching cluster details is a potentially expensive call to clouddriver, but allows us to
   * provide a definitive list of accounts that an application has a presence in.
   *
   * As a trade-off, we'll fetch cluster details on the background refresh loop and merge in the
   * account details when applications are requested on-demand.
   *
   * @param expandClusterNames Should cluster details (for each application) be fetched from clouddriver
   * @return Applications
   */
  List<Map<String, Object>> tick(boolean expandClusterNames = true) {
    List<List<Map>> all
    if (applicationConfigurationProperties.useFront50AsSourceOfTruth) {
      all = getApplicationsWithFront50AsSourceOfTruth(expandClusterNames)
    } else {
      def applicationListRetrievers = buildApplicationListRetrievers(expandClusterNames)
      List<Future<List<Map>>> futures = executorService.invokeAll(applicationListRetrievers)
      try {
        all = futures.collect { it.get() }
      } catch (ExecutionException ee) {
        throw ee.cause
      }
    }

    List<Map> flat = (List<Map>) all?.flatten()?.toList()
    return mergeApps(flat, serviceConfiguration.getService('front50')).collect {
      it.attributes
    } as List<Map>
  }

  List<Map> getAllApplications() {
    return allApplicationsCache.get()
  }

  Map getApplication(String name, boolean expand) {
    List<Map> applications
    if (applicationConfigurationProperties.useFront50AsSourceOfTruth) {
      applications = getApplicationWithFront50AsSourceOfTruth(name, expand)
    } else {
      def applicationRetrievers = buildApplicationRetrievers(name, expand)
      def futures = executorService.invokeAll(applicationRetrievers)
      try {
        applications = (List<Map>) futures.collect { it.get() }
      } catch (ExecutionException ee) {
        throw ee.cause
      }

      if (!expand) {
        def cachedApplication = allApplicationsCache.get().find { name.equalsIgnoreCase(it.name as String) }
        if (cachedApplication) {
          // ensure that `cachedApplication` attributes are overridden by any previously fetched metadata from front50
          applications.add(0, cachedApplication)
        }
      }
    }
    List<Map> mergedApps = mergeApps(applications, serviceConfiguration.getService('front50'))
    return mergedApps ? mergedApps[0] : null
  }

  List<Map> getApplicationHistory(String name, int maxResults) {
    return front50Service.getApplicationHistory(name, maxResults)
  }

  List<Map> getPipelineConfigsForApplication(String app) {
    return front50Service.getPipelineConfigsForApplication(app, true)
  }

  Map getPipelineConfigForApplication(String app, String pipelineNameOrId) {
    return front50Service.getPipelineConfigsForApplication(app, true)
      .find { it.name == pipelineNameOrId || it.id == pipelineNameOrId }
  }

  List<Map> getStrategyConfigsForApplication(String app) {
    return front50Service.getStrategyConfigs(app)
  }

  private Collection<Callable<List<Map>>> buildApplicationListRetrievers(boolean expandClusterNames) {
    [
      new Front50ApplicationListRetriever(front50Service, allApplicationsCache) as Callable<List<Map>>,
      new ClouddriverApplicationListRetriever(
        clouddriverServiceSelector.select(),
        allApplicationsCache,
        expandClusterNames) as Callable<List<Map>>
    ]
  }

  private Collection<Callable<Map>> buildApplicationRetrievers(String applicationName, boolean expand) {
    def retrievers = [
      new Front50ApplicationRetriever(applicationName, front50Service, allApplicationsCache) as Callable<Map>
    ]
    if (expand) {
      retrievers.add(
        new ClouddriverApplicationRetriever(
          applicationName,
          clouddriverServiceSelector.select()) as Callable<Map>
      )
    }
    return retrievers
  }

  @CompileDynamic
  private static List<Map> mergeApps(List<Map<String, Object>> applications, Service applicationServiceConfig) {
    Map<String, Map<String, Object>> merged = [:]
    for (Map<String, Object> app in applications) {
      if (!app?.name) {
        continue
      }

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

        Set<String> accounts = (app.clusters as Map)?.keySet() ?: (app.clusterNames as Map)?.keySet()
        if (accounts) {
          if (mergedApp.attributes.cloudDriverAccounts) {
            accounts = new HashSet<String>(accounts)
            accounts.addAll(mergedApp.attributes.cloudDriverAccounts.split(',').toList())
          }
          mergedApp.attributes.cloudDriverAccounts = accounts.join(',')
        }

        (app["attributes"] as Map).entrySet().each {
          if (it.value && !(mergedApp.attributes as Map)[it.key]) {
            // don't overwrite existing attributes with metadata from clouddriver
            (mergedApp.attributes as Map)[it.key] = it.value
          }
        }
      } else {
        Map attributes = app.attributes ?: app
        attributes.entrySet().each {
          if (it.value != null) {
            (mergedApp.attributes as Map)[it.key] = it.value
          }
        }
      }

      // ensure that names are consistently lower-cased.
      mergedApp.name = key.toLowerCase()
      mergedApp.attributes['name'] = mergedApp.name
    }

    // Overwrite any Front50 account names
    merged.each { key, mergedApp ->
      if (mergedApp.attributes.cloudDriverAccounts) {
        mergedApp.attributes.accounts = mergedApp.attributes.cloudDriverAccounts
        (mergedApp.attributes as Map).remove('cloudDriverAccounts')
      }
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
  }

  static String mergeAccounts(String accounts1, String accounts2) {
    return [accounts1, accounts2].collect { String s ->
      s?.split(',')?.toList()?.findResults { it.trim() ?: null } ?: []
    }.flatten().toSet().sort().join(',')
  }

  /**
   * gets the applications from front50 and clouddriver, and only considers the applications
   * returned from front50 to be the source of truth. All applications obtained from clouddriver
   * that are not known to front50 are ignored
   *
   * @param expandClusterNames gets passed along to the ClouddriverApplicationListRetriever
   * @return a list of type List<Map> that contains the responses from front50 and clouddriver
   */
  private List<List<Map>> getApplicationsWithFront50AsSourceOfTruth(boolean expandClusterNames) {
    List<Map> front50Apps, clouddriverApps
    try {
      Future<List<Map>> front50future = executorService.submit(
        new Front50ApplicationListRetriever(front50Service, allApplicationsCache) as Callable<List<Map>>
      )

      Future<List<Map>> clouddriverFuture = executorService.submit(
        new ClouddriverApplicationListRetriever(
          clouddriverServiceSelector.select(),
          allApplicationsCache,
          expandClusterNames) as Callable<List<Map>>
      )
      // capture the results from both front50 and clouddriver
      front50Apps = front50future.get()
      clouddriverApps = clouddriverFuture.get()
    } catch (ExecutionException ee) {
      log.error("error occurred when retrieving applications. Error: ", ee)
      throw ee.cause
    }

    // get all app names from front50. This becomes our source of truth for known applications
    Set<String> allFront50AppNames = front50Apps.stream()
      .map({ it -> it.get("name") as String })
      .collect(Collectors.toSet())

    // iterate through all the results from clouddriver and only consider those applications that
    // are known to front50
    Set<Map> clouddriverAppsKnownToFront50 = clouddriverApps.stream()
      .filter({ it ->
        String clouddriverAppName = it.get("name")
        allFront50AppNames.any { front50AppName -> front50AppName.equalsIgnoreCase(clouddriverAppName) }
      })
      .collect(Collectors.toSet())

    List<List<Map>> all = []
    all.add(front50Apps)
    all.add(clouddriverAppsKnownToFront50.toList())
    return all
  }

  /**
   * gets the application from front50 first. If it does not contain the application, then processing
   * stops as there is no need to check clouddriver for the application. Otherwise, depending on
   * the parameter expand, clouddriver is queried for the application.
   *
   * @param name application name
   * @param expand if true, then clouddriver is queried for the application only if front50 contains
   *        the application
   * @return a list of type Map that contains the responses from front50 and/or clouddriver
   */
  private List<Map> getApplicationWithFront50AsSourceOfTruth(String name, boolean expand) {
    Map front50App, clouddriverApp
    List<Map> result = []
    try {
      Future<Map> front50future = executorService.submit(
        new Front50ApplicationRetriever(name, front50Service, allApplicationsCache) as Callable<Map>
      )
      // capture the result from front50
      front50App = front50future.get()
      if (front50App) {
        result.add(front50App)
        if (expand) {
          Future<Map> clouddriverFuture = executorService.submit(
            new ClouddriverApplicationRetriever(name, clouddriverServiceSelector.select()) as Callable<Map>
          )
          // capture the result from clouddriver
          clouddriverApp = clouddriverFuture.get()
          String clouddriverAppName = clouddriverApp.get("name")
          if (clouddriverAppName?.equalsIgnoreCase(front50App.get("name") as String)) {
            result.add(clouddriverApp)
          }
        }
      }
    } catch (ExecutionException ee) {
      log.error("error occurred when retrieving application: ${name}. Error: ", ee)
      throw ee.cause
    }

    return result
  }

  static class Front50ApplicationListRetriever implements Callable<List<Map>> {
    private final Front50Service front50
    private final AtomicReference<List<Map>> allApplicationsCache
    private final Object principal

    Front50ApplicationListRetriever(Front50Service front50, AtomicReference<List<Map>> allApplicationsCache) {
      this.front50 = front50
      this.allApplicationsCache = allApplicationsCache
      this.principal = SecurityContextHolder.context?.authentication?.principal
    }

    @Override
    List<Map> call() throws Exception {
      try {
        AuthenticatedRequest.propagate({
          try {
            return AuthenticatedRequest.allowAnonymous { front50.getAllApplicationsUnrestricted() }
          } catch (RetrofitError e) {
            if (e.response?.status == 404) {
              return []
            } else {
              throw e
            }
          }
        }, false, principal).call() as List<Map>
      } catch (Exception e) {
        log.error("Falling back to application cache", e)
        return allApplicationsCache.get()
      }
    }
  }

  static class Front50ApplicationRetriever implements Callable<Map> {
    private final String name
    private final Front50Service front50
    private final AtomicReference<List<Map>> allApplicationsCache
    private final Object principal

    Front50ApplicationRetriever(String name,
                                Front50Service front50,
                                AtomicReference<List<Map>> allApplicationsCache) {
      this.name = name
      this.front50 = front50
      this.allApplicationsCache = allApplicationsCache
      this.principal = SecurityContextHolder.context?.authentication?.principal
    }

    @Override
    Map call() throws Exception {
      try {
        AuthenticatedRequest.propagate({
          try {
            def metadata = front50.getApplication(name)
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
      } catch (Exception e) {
        log.error("Falling back to application cache", e)
        return allApplicationsCache.get().find { name.equalsIgnoreCase(it.name as String) }
      }
    }
  }

  static class ClouddriverApplicationListRetriever implements Callable<List<Map>> {
    private final ClouddriverService clouddriver
    private final Object principal
    private final AtomicReference<List<Map>> allApplicationsCache
    private final boolean expandClusterNames

    ClouddriverApplicationListRetriever(ClouddriverService clouddriver,
                                        AtomicReference<List<Map>> allApplicationsCache,
                                        boolean expandClusterNames) {
      this.clouddriver = clouddriver
      this.allApplicationsCache = allApplicationsCache
      this.expandClusterNames = expandClusterNames
      this.principal = SecurityContextHolder.context?.authentication?.principal
    }

    @Override
    List<Map> call() throws Exception {
      try {
        AuthenticatedRequest.propagate({
          try {
            return AuthenticatedRequest.allowAnonymous { clouddriver.getAllApplicationsUnrestricted(expandClusterNames) }
          } catch (RetrofitError e) {
            if (e.response?.status == 404) {
              return []
            } else {
              throw e
            }
          }
        }, false, principal).call() as List<Map>
      } catch (Exception e) {
        log.error("Falling back to application cache", e)
        return allApplicationsCache.get()
      }
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
      try {
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
      } catch (Exception e) {
        log.error("Falling back to empty map", e)
        return [:]
      }
    }
  }
}
