/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.internal.services

import com.netflix.spinnaker.gate.services.commands.HystrixFactory
import com.netflix.spinnaker.internal.services.internal.FlexService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class ElasticIpService {
  private static final String GROUP = "elasticIps"

  @Autowired
  FlexService flexService

  List<Map> getForCluster(String application, String account, String cluster) {
    HystrixFactory.newListCommand(GROUP, "getElasticIpsForCluster") {
      flexService.getForCluster(application, account, cluster)
    } execute()
  }

  List<Map> getForClusterAndRegion(String application, String account, String cluster, String region) {
    HystrixFactory.newListCommand(GROUP, "getElasticIpsForClusterAndRegion") {
      flexService.getForClusterAndRegion(application, account, cluster, region)
    } execute()
  }

  List<Map> getForAccount(String account) {
    HystrixFactory.newListCommand(GROUP, "getElasticIpsForAccount") {
      return flexService.getForAccount(account)
    } execute()
  }

  List<Map> getForAccountAndRegion(String account, String region) {
    HystrixFactory.newListCommand(GROUP, "getElasticIpsForAccountAndRegion") {
      return flexService.getForAccountAndRegion(account, region)
    } execute()
  }
}
