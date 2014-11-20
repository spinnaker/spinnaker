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

import com.google.common.base.Preconditions
import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.MayoService
import com.netflix.spinnaker.gate.services.internal.OortService
import com.netflix.spinnaker.gate.services.internal.OrcaService
import groovy.transform.CompileStatic
import java.util.concurrent.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.converter.ConversionException

@CompileStatic
@Component
class ApplicationService {
  private static final String GROUP = "applications"

  @Autowired
  OortService oortService

  @Autowired
  MayoService mayoService

  @Autowired
  OrcaService orcaService

  @Autowired
  Front50Service front50Service

  @Autowired
  CredentialsService credentialsService

  @Autowired
  ExecutorService executorService

  List<Map> getAll() {
    def applicationListRetrievers = (credentialsService.accountNames.collect {
      new ApplicationListRetriever(it, front50Service)
    } as Collection<Callable<List<Map>>>)

    HystrixFactory.newListCommand(GROUP, "getAll", true) {
      List<Future<List<Map>>> futures = executorService.invokeAll(applicationListRetrievers)
      List<List<Map>> all = futures.collect { it.get() } // spread operator doesn't work here; no clue why.
      List<Map> flat = (List<Map>) all?.flatten()?.toList()
      flat
    } execute()
  }

  Map get(String name) {
    def applicationRetrievers = (credentialsService.accountNames.collectMany {
      [new Front50ApplicationRetriever(it, name, front50Service), new OortApplicationRetriever(name, oortService)]
    } as Collection<Callable<Map>>)

    HystrixFactory.newMapCommand(GROUP, "getApp-${name}".toString(), true) {
      def futures = executorService.invokeAll(applicationRetrievers)
      List<Map> applications = (List<Map>) futures.collect { it.get() }
      applications.inject([:]) { LinkedHashMap result, Map app ->
        if (app) {
          if (app.containsKey("clusters")) {
            result.putAll(app)
          } else {
            if (!result.containsKey("attributes")) {
              result.attributes = [:]
            }
            for (Map.Entry<String, String> entry in ((Map<String, String>) app).entrySet()) {
              if (entry.value) {
                result.attributes[entry.key] = entry.value
              }
            }
          }
        }
        result
      } as Map
    } execute()
  }

  List getTasks(String app) {
    Preconditions.checkNotNull(app)
    orcaService.getTasks(app)
  }

  List getPipelines(String app) {
    Preconditions.checkNotNull(app)
    orcaService.getPipelines(app)
  }

  List<Map> getPipelineConfigs(String app) {
    HystrixFactory.newListCommand(GROUP, "getPipelineConfigs-${app}".toString(), true) {
      mayoService.getPipelineConfigs(app)
    } execute()
  }

  Map getPipelineConfig(String app, String pipelineName) {
    HystrixFactory.newMapCommand(GROUP, "getPipelineConfig-${app}-${pipelineName}".toString(), true) {
      mayoService.getPipelineConfig(app, pipelineName)
    } execute()
  }

  Map bake(String application, String pkg, String baseOs, String baseLabel, String region) {
    orcaService.doOperation([application: application, description: "Bake (Gate)",
                             job        : [[type  : "bake", "package": pkg, baseOs: baseOs, baseLabel: baseLabel,
                                            region: region, user: "gate"]]])
  }

  Map delete(String account, String name) {
    front50Service.delete(account, name)
  }

  Map create(Map<String, String> app) {
    def account = app.remove("account")
    orcaService.doOperation([application: app.name, description: "Create application (Gate)",
                             job        : [[type: "createApplication", application: app, account: account]]])
  }

  static class ApplicationListRetriever implements Callable<List<Map>> {
    private final String account
    private final Front50Service front50

    ApplicationListRetriever(String account, Front50Service front50) {
      this.account = account
      this.front50 = front50
    }

    @Override
    List<Map> call() throws Exception {
      try {
        return front50.getAll(account)
      } catch (RetrofitError e) {
        if (e.response.status == 404) {
          return []
        } else {
          throw e
        }
      }
    }
  }

  static class Front50ApplicationRetriever implements Callable<Map> {
    private final String account
    private final String name
    private final Front50Service front50

    Front50ApplicationRetriever(String account, String name, Front50Service front50) {
      this.account = account
      this.name = name
      this.front50 = front50
    }

    @Override
    Map call() throws Exception {
      try {
        def metadata = front50.getMetaData(account, name)
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
    }
  }

  static class OortApplicationRetriever implements Callable<Map> {
    private final String name
    private final OortService oort

    OortApplicationRetriever(String name, OortService oort) {
      this.name = name
      this.oort = oort
    }

    @Override
    Map call() throws Exception {
      try {
        return oort.getApplication(name)
      } catch (RetrofitError e) {
        if (e.response.status == 404) {
          return [:]
        } else {
          throw e
        }
      }
    }
  }
}
