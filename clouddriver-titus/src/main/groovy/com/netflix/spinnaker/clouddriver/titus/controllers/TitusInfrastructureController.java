/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.controllers;

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/titus")
public class TitusInfrastructureController {

  @Autowired
  TitusClientProvider titusClientProvider;

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider;

  @RequestMapping(value = "/job/{account}/{region}/{jobId}", method = RequestMethod.GET)
  Object getJobDetails(@PathVariable("account") String account, @PathVariable("region") String region, @PathVariable("jobId") String jobId) {
    return titusClientProvider.getTitusClient((NetflixTitusCredentials) accountCredentialsProvider.getCredentials(account), region).getJobJson(jobId);
  }

  @RequestMapping(value = "/task/{account}/{region}/{taskId}", method = RequestMethod.GET)
  Object getTaskDetails(@PathVariable("account") String account, @PathVariable("region") String region, @PathVariable("taskId") String taskId) {
    return titusClientProvider.getTitusClient((NetflixTitusCredentials) accountCredentialsProvider.getCredentials(account), region).getTaskJson(taskId);
  }

}
