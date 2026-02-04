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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.model.Network
import com.netflix.spinnaker.clouddriver.model.NetworkProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import io.reactivex.rxjava3.core.Observable

@RequestMapping("/networks")
@RestController
class NetworkController {

  @Autowired
  List<NetworkProvider> networkProviders

  @RequestMapping(method = RequestMethod.GET)
  Map<String, Set<Network>> list() {
    Observable.fromIterable (networkProviders).flatMap { networkProvider ->
      Observable.fromIterable (networkProvider.getAll())
    } filter {
      it != null
    } reduce([:], { Map networks, Network network ->
      if (!networks.containsKey(network.cloudProvider)) {
        networks[network.cloudProvider] = sortedTreeSet
      }
      networks[network.cloudProvider] << network
      networks
    }) blockingGet()
  }

  @RequestMapping(method = RequestMethod.GET, value = "/{cloudProvider}")
  Set<Network> listByCloudProvider(@PathVariable String cloudProvider) {
    networkProviders.findAll { networkProvider ->
      networkProvider.cloudProvider == cloudProvider
    } collectMany {
      it.all
    }
  }

  private static Set<Network> getSortedTreeSet() {
    new TreeSet<>({ Network a, Network b ->
      a.name?.toLowerCase() <=> b.name?.toLowerCase() ?:
        a.id <=> b.id ?:
        a.account <=> b.account ?:
        a.region <=> b.region
    } as Comparator)
  }
}
