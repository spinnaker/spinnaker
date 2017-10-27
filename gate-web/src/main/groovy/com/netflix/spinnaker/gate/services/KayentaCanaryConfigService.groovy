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
import retrofit.client.Response
import retrofit.mime.TypedByteArray


@CompileStatic
@Component
@Slf4j
@ConditionalOnExpression('${services.kayenta.enabled:false} and ${services.kayenta.canaryConfigStore:false}')
class KayentaCanaryConfigService implements CanaryConfigService {
  private static final String GROUP = "canaryConfigs"

  @Autowired
  KayentaService kayentaService

  List getCanaryConfigs(String application) {
    HystrixFactory.newListCommand(GROUP, "getCanaryConfigs") {
      kayentaService.getCanaryConfigs(application)
    } execute()
  }

  Map getCanaryConfig(String id) {
    HystrixFactory.newMapCommand(GROUP, "getCanaryConfig") {
      kayentaService.getCanaryConfig(id)
    } execute()
  }

  String createCanaryConfig(Map config) {
    HystrixFactory.newMapCommand(GROUP, "createCanaryConfig") {
      Response response = kayentaService.createCanaryConfig(config)
      return new String(((TypedByteArray)response.getBody()).getBytes())
    } execute()
  }

  String updateCanaryConfig(String id, Map config) {
    HystrixFactory.newMapCommand(GROUP, "updateCanaryConfig") {
      Response response = kayentaService.updateCanaryConfig(id, config)
      return new String(((TypedByteArray)response.getBody()).getBytes())
    } execute()
  }

  void deleteCanaryConfig(String id) {
    HystrixFactory.newMapCommand(GROUP, "deleteCanaryConfig") {
      kayentaService.deleteCanaryConfig(id)
    } execute()
  }
}
