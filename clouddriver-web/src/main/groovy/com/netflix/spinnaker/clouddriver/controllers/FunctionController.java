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

import com.netflix.discovery.converters.Auto;
import com.netflix.spinnaker.clouddriver.model.Function;
import com.netflix.spinnaker.clouddriver.model.FunctionProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class FunctionController {
  private final List<FunctionProvider> functionProviders;

  @Autowired
  public FunctionController(Optional<List<FunctionProvider>> functionProviders) {
    this.functionProviders = functionProviders.orElse(Collections.emptyList());
  }

  @PostAuthorize("@authorizationSupport.filterForAccounts(returnObject)")
  @RequestMapping(value = "/functions", method = RequestMethod.GET)
  List<Function> list() {
    return functionProviders.stream()
      .map(FunctionProvider::getAllFunctions)
      .flatMap(Collection::stream)
      .collect(Collectors.toList());
  }
}
