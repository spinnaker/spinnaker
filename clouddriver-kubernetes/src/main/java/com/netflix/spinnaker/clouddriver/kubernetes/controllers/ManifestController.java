/*
 * Copyright 2017 Google, Inc.
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

import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.KubernetesManifestProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider.KubernetesManifestProvider.Sort;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.model.Manifest;
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.List;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manifests")
public class ManifestController {
  private static final Logger log = LoggerFactory.getLogger(ManifestController.class);
  final KubernetesManifestProvider manifestProvider;

  final RequestQueue requestQueue;

  @Autowired
  public ManifestController(
      KubernetesManifestProvider manifestProvider, RequestQueue requestQueue) {
    this.manifestProvider = manifestProvider;
    this.requestQueue = requestQueue;
  }

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @PostAuthorize("hasPermission(returnObject?.moniker?.app, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/{account:.+}/_/{name:.+}", method = RequestMethod.GET)
  Manifest getForAccountAndName(
      @PathVariable String account,
      @PathVariable String name,
      @RequestParam(value = "includeEvents", required = false, defaultValue = "true")
          boolean includeEvents) {
    return getForAccountLocationAndName(account, "", name, includeEvents);
  }

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @PostAuthorize("hasPermission(returnObject?.moniker?.app, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/{account:.+}/{location:.+}/{name:.+}", method = RequestMethod.GET)
  Manifest getForAccountLocationAndName(
      @PathVariable String account,
      @PathVariable String location,
      @PathVariable String name,
      @RequestParam(value = "includeEvents", required = false, defaultValue = "true")
          boolean includeEvents) {

    Manifest manifest;
    try {
      manifest =
          requestQueue.execute(
              account, () -> manifestProvider.getManifest(account, location, name, includeEvents));
    } catch (Throwable t) {
      log.warn("Failed to read manifest ", t);
      return null;
    }

    String request =
        String.format("(account: %s, location: %s, name: %s)", account, location, name);
    if (manifest == null) {
      throw new NotFoundException("Manifest " + request + " not found");
    }

    return manifest;
  }

  @RequestMapping(value = "/{account:.+}/{name:.+}", method = RequestMethod.GET)
  Manifest getForAccountLocationAndName(
      @PathVariable String account,
      @PathVariable String name,
      @RequestParam(value = "includeEvents", required = false, defaultValue = "true")
          boolean includeEvents) {
    return getForAccountLocationAndName(account, "", name, includeEvents);
  }

  @RequestMapping(
      value =
          "/{account:.+}/{location:.+}/{kind:.+}/cluster/{app:.+}/{cluster:.+}/dynamic/{criteria:.+}",
      method = RequestMethod.GET)
  KubernetesCoordinates getDynamicManifestFromCluster(
      @PathVariable String account,
      @PathVariable String location,
      @PathVariable String kind,
      @PathVariable String app,
      @PathVariable String cluster,
      @PathVariable Criteria criteria) {
    final String request =
        String.format(
            "(account: %s, location: %s, kind: %s, app %s, cluster: %s, criteria: %s)",
            account, location, kind, app, cluster, criteria);

    List<KubernetesManifest> manifests;
    try {
      manifests =
          requestQueue.execute(
              account,
              () ->
                  manifestProvider.getClusterAndSortAscending(
                      account, location, kind, cluster, criteria.getSort()));
    } catch (Throwable t) {
      log.warn("Failed to read {}", request, t);
      return null;
    }

    if (manifests == null) {
      throw new NotFoundException("No manifests matching " + request + " found");
    }

    try {
      switch (criteria) {
        case oldest:
        case smallest:
          return KubernetesCoordinates.fromManifest(manifests.get(0));
        case newest:
        case largest:
          return KubernetesCoordinates.fromManifest(manifests.get(manifests.size() - 1));
        case second_newest:
          return KubernetesCoordinates.fromManifest(manifests.get(manifests.size() - 2));
        default:
          throw new IllegalArgumentException("Unknown criteria: " + criteria);
      }
    } catch (IndexOutOfBoundsException e) {
      throw new NotFoundException("No manifests matching " + request + " found");
    }
  }

  enum Criteria {
    oldest(Sort.AGE),
    newest(Sort.AGE),
    second_newest(Sort.AGE),
    largest(Sort.SIZE),
    smallest(Sort.SIZE);

    @Getter private final Sort sort;

    Criteria(Sort sort) {
      this.sort = sort;
    }
  }
}
