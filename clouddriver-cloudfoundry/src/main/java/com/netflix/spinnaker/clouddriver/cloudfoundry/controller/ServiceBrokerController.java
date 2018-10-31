/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.controller;

import com.netflix.spinnaker.clouddriver.cloudfoundry.servicebroker.Service;
import com.netflix.spinnaker.clouddriver.cloudfoundry.servicebroker.ServiceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
@RestController
@RequestMapping("/servicebroker")
public class ServiceBrokerController {
  private final Collection<ServiceProvider> serviceProviders;

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @GetMapping("/{account}/services")
  public Collection<Service> listServices(@RequestParam(value = "cloudProvider") String cloudProvider,
                                          @RequestParam(value = "region") String region,
                                          @PathVariable String account) {
    return serviceProviders.stream()
      .filter(serviceProvider -> serviceProvider.getCloudProvider().equals(cloudProvider))
      .flatMap(serviceProvider -> serviceProvider.getServices(account, region).stream())
      .sorted(comparing(Service::getName))
      .collect(toList());
  }
}
