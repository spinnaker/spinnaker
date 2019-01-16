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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.exceptions.CloudProviderNotFoundException
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import java.util.stream.Collectors

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
class LoadBalancerController {

  @Autowired
  List<LoadBalancerProvider> loadBalancerProviders

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @PostAuthorize("@authorizationSupport.filterForAccounts(returnObject)")
  @RequestMapping(value = "/applications/{application}/loadBalancers", method = RequestMethod.GET)
  List<LoadBalancer> list(@PathVariable String application) {
    loadBalancerProviders.findResults {
      it.getApplicationLoadBalancers(application)
    }
    .flatten()
    .sort { a, b -> a.name.toLowerCase() <=> b.name.toLowerCase() } as List<LoadBalancer>
  }

  @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission()")
  @PostAuthorize("@authorizationSupport.filterLoadBalancerProviderItems(returnObject)")
  @RequestMapping(value = "/{cloudProvider:.+}/loadBalancers", method = RequestMethod.GET)
  List<LoadBalancerProvider.Item> listForCloudProvider(@PathVariable String cloudProvider) {
    return findLoadBalancerProviders(cloudProvider).stream()
        .flatMap({ (it.list() ?: []).stream() })
        .collect(Collectors.toList())
  }

  @PostAuthorize("@authorizationSupport.filterLoadBalancerProviderItems(returnObject)")
  @RequestMapping(value = "/{cloudProvider:.+}/loadBalancers/{name:.+}", method = RequestMethod.GET)
  LoadBalancerProvider.Item get(@PathVariable String cloudProvider,
                                @PathVariable String name) {
    return findLoadBalancerProviders(cloudProvider)
        .stream()
        .map({ return it.get(name) })
        .filter({ return it != null })
        .findFirst()
        .orElse(null)
  }

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(value = "/{cloudProvider:.+}/loadBalancers/{account:.+}/{region:.+}/{name:.+}",
                  method = RequestMethod.GET)
  List<LoadBalancerProvider.Details> getByAccountRegionName(@PathVariable String cloudProvider,
                                                            @PathVariable String account,
                                                            @PathVariable String region,
                                                            @PathVariable String name) {
    return findLoadBalancerProviders(cloudProvider).stream()
        .map({ return it.byAccountAndRegionAndName(account, region, name) ?: [] })
        .flatMap({ return it.stream() })
        .collect(Collectors.toList())
  }

  private List<LoadBalancerProvider> findLoadBalancerProviders(String cloudProvider) {
    def result = loadBalancerProviders
        .stream()
        .filter({ it.cloudProvider == cloudProvider })
        .collect(Collectors.toList())

    if (!result) {
      throw new CloudProviderNotFoundException("No cloud provider named ${cloudProvider} found")
    }

    return result
  }
}
