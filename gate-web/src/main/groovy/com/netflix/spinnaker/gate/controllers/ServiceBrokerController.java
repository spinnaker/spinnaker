/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.ServiceBrokerService;
import groovy.util.logging.Slf4j;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/servicebroker")
public class ServiceBrokerController {
  private final ServiceBrokerService serviceBrokerService;

  public ServiceBrokerController(ServiceBrokerService serviceBrokerService) {
    this.serviceBrokerService = serviceBrokerService;
  }

  @RequestMapping(value = "{account}/services", method = RequestMethod.GET)
  public List<Map> listServices(
      @RequestParam(value = "cloudProvider", required = false) String cloudProvider,
      @RequestParam(value = "region") String region,
      @PathVariable String account) {
    return serviceBrokerService.listServices(cloudProvider, region, account);
  }

  @RequestMapping(value = "{account}/serviceInstance", method = RequestMethod.GET)
  public Map getServiceInstance(
      @PathVariable(value = "account") String account,
      @RequestParam(value = "cloudProvider") String cloudProvider,
      @RequestParam(value = "region") String region,
      @RequestParam(value = "serviceInstanceName") String serviceInstanceName) {
    return serviceBrokerService.getServiceInstance(
        account, cloudProvider, region, serviceInstanceName);
  }
}
