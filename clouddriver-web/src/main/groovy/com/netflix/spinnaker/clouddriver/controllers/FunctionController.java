/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers;

import com.netflix.spinnaker.clouddriver.model.Function;
import com.netflix.spinnaker.clouddriver.model.FunctionProvider;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FunctionController {
  private final List<FunctionProvider> functionProviders;
  private HashMap<String, String> functionMap = new HashMap<String, String>();

  @Autowired
  public FunctionController(Optional<List<FunctionProvider>> functionProviders) {
    this.functionProviders = functionProviders.orElse(Collections.emptyList());
  }

  @PostAuthorize("@authorizationSupport.filterForAccounts(returnObject)")
  @RequestMapping(value = "/functions", method = RequestMethod.GET)
  @ResponseBody
  public List<Function> list(
      @RequestParam(value = "functionName", required = false) String functionName,
      @RequestParam(value = "region", required = false) String region,
      @RequestParam(value = "account", required = false) String account) {
    if (functionName == null || functionName.isEmpty()) {
      return functionProviders.stream()
          .map(FunctionProvider::getAllFunctions)
          .flatMap(Collection::stream)
          .collect(Collectors.toList());
    } else {
      try {
        List<Function> myFunction =
            functionProviders.stream()
                .map(
                    functionProvider -> functionProvider.getFunction(account, region, functionName))
                .collect(Collectors.toList());
        return myFunction;
      } catch (NotFoundException e) {
        throw new NotFoundException(functionName + "does not exist");
      }
    }
  }
}
