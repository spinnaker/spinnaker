/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.gate.services.internal.KayentaService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedByteArray

import static com.netflix.spinnaker.gate.retrofit.UpstreamBadRequest.classifyError

@CompileStatic
@Component
@Slf4j
@ConditionalOnExpression('${services.kayenta.enabled:false} and ${services.kayenta.canaryConfigStore:false}')
class KayentaCanaryConfigService implements CanaryConfigService {

  private static final String HYSTRIX_GROUP = "v2-canaryConfigs"

  @Autowired
  KayentaService kayentaService

  List getCanaryConfigs(String application, String configurationAccountName) {
    return HystrixFactory.newListCommand(HYSTRIX_GROUP, "getCanaryConfigs", {
      try {
        return kayentaService.getCanaryConfigs(application, configurationAccountName)
      } catch (RetrofitError e) {
        throw classifyError(e)
      }
    }).execute() as List
  }

  Map getCanaryConfig(String id, String configurationAccountName) {
    return HystrixFactory.newMapCommand(HYSTRIX_GROUP, "getCanaryConfig", {
      try {
        return kayentaService.getCanaryConfig(id, configurationAccountName)
      } catch (RetrofitError e) {
        throw classifyError(e)
      }
    }).execute() as Map
  }

  Map createCanaryConfig(Map config, String configurationAccountName) {
    return HystrixFactory.newMapCommand(HYSTRIX_GROUP, "createCanaryConfig", {
      try {
        return kayentaService.createCanaryConfig(config, configurationAccountName)
      } catch (RetrofitError e) {
        throw classifyError(e)
      }
    }).execute() as Map
  }

  Map updateCanaryConfig(String id, Map config, String configurationAccountName) {
    return HystrixFactory.newMapCommand(HYSTRIX_GROUP, "updateCanaryConfig", {
      try {
        return kayentaService.updateCanaryConfig(id, config, configurationAccountName)
      } catch (RetrofitError e) {
        throw classifyError(e)
      }
    }).execute() as Map
  }

  void deleteCanaryConfig(String id, String configurationAccountName) {
    HystrixFactory.newVoidCommand(HYSTRIX_GROUP, "deleteCanaryConfig", {
      try {
        kayentaService.deleteCanaryConfig(id, configurationAccountName)
      } catch (RetrofitError e) {
        throw classifyError(e)
      }
    }).execute()
  }
}
