/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.controllers;

import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.model.KubernetesRawResource;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.KubernetesRawResourceProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
class RawResourceController {
  private final KubernetesRawResourceProvider rawResourceProvider;

  @Autowired
  public RawResourceController(KubernetesRawResourceProvider rawResourceProvider) {
    this.rawResourceProvider = rawResourceProvider;
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @PostAuthorize("@authorizationSupport.filterForAccounts(returnObject)")
  @RequestMapping(value = "/applications/{application}/rawResources", method = RequestMethod.GET)
  List<KubernetesRawResource> list(@PathVariable String application) {
    return new ArrayList<>(rawResourceProvider.getApplicationRawResources(application));
  }
}
