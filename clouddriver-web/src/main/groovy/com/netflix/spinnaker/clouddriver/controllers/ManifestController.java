/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.controllers;

import com.netflix.spinnaker.clouddriver.model.Manifest;
import com.netflix.spinnaker.clouddriver.model.ManifestProvider;
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/applications/{application:.+}/manifests")
public class ManifestController {
  final List<ManifestProvider> manifestProviders;

  final RequestQueue requestQueue;

  @Autowired
  public ManifestController(List<ManifestProvider> manifestProviders, RequestQueue requestQueue) {
    this.manifestProviders = manifestProviders;
    this.requestQueue = requestQueue;
  }

 // @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ') && hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(value = "/{account:.+}/{location:.+}/{name:.+}", method = RequestMethod.GET)
  Collection<Manifest> getForAccountLocationAndName(@PathVariable String application,
      @PathVariable String account,
      @PathVariable String location,
      @PathVariable String name) {
    Collection<Manifest> manifests = manifestProviders.stream()
        .map(provider -> {
          try {
            return requestQueue.execute(application, () -> provider.getManifest(account, location, name));
          } catch (Throwable t) {
            log.warn("Failed to read manifest " , t);
            return null;
          }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    if (manifests.isEmpty()) {
      throw new NotFoundException(String.format("Manifest not found (application: %s, account: %s, location: %s, name: %s)",
          application, account, location, name));
    }

    return manifests;
  }
}
