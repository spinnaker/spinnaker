/*
 * Copyright (c) 2019 Schibsted Media Group.
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

package com.netflix.spinnaker.clouddriver.aws.controllers;

import com.netflix.spinnaker.clouddriver.aws.model.AmazonCloudFormationStack;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonCloudFormationProvider;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
@RequestMapping("/aws/cloudFormation/stacks")
@RestController
class CloudFormationController {

  private AmazonCloudFormationProvider cloudFormationProvider;

  @Autowired
  public CloudFormationController(AmazonCloudFormationProvider cloudFormationProvider) {
    this.cloudFormationProvider = cloudFormationProvider;
  }

  @RequestMapping(method = RequestMethod.GET)
  List<AmazonCloudFormationStack> list(
      @RequestParam String accountName,
      @RequestParam(required = false, defaultValue = "*") String region) {
    log.debug("Cloud formation list stacks for account {}", accountName);
    return cloudFormationProvider.list(accountName, region);
  }

  @RequestMapping(method = RequestMethod.GET, value = "/**")
  AmazonCloudFormationStack get(HttpServletRequest request) {
    String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String stackId =
        new AntPathMatcher().extractPathWithinPattern(pattern, request.getRequestURI());
    log.debug("Cloud formation get stack with id {}", stackId);
    return cloudFormationProvider
        .get(stackId)
        .orElseThrow(
            () ->
                new NotFoundException(
                    String.format("Cloud Formation stackId %s not found.", stackId)));
  }
}
