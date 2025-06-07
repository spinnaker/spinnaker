/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.ElasticIp
import com.netflix.spinnaker.clouddriver.model.ElasticIpProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.reactivex.rxjava3.core.Observable

@RequestMapping("/elasticIps")
@RestController
class ElasticIpController {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  List<ElasticIpProvider> elasticIpProviders

  @RequestMapping(method = RequestMethod.GET, value = "/{account}")
  Set<ElasticIp> listByAccount(@PathVariable String account) {
    Observable.fromIterable (elasticIpProviders).flatMap {
      Observable.fromIterable (it.getAllByAccount(account))
    } reduce(new HashSet<ElasticIp>(), { Set elasticIps, ElasticIp elasticIp ->
      elasticIps << elasticIp
      elasticIps
    }) blockingGet()
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{account}", params = ['region'])
  Set<ElasticIp> listByAccountAndRegion(@PathVariable String account, @RequestParam("region") String region) {
    Observable.fromIterable (elasticIpProviders).flatMap {
      Observable.fromIterable (it.getAllByAccountAndRegion(account, region))
    } reduce(new HashSet<ElasticIp>(), { Set elasticIps, ElasticIp elasticIp ->
      elasticIps << elasticIp
      elasticIps
    }) blockingGet()
  }
}
