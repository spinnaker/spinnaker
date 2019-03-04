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

import com.netflix.spinnaker.clouddriver.model.ServerGroupManager;
import com.netflix.spinnaker.clouddriver.model.ServerGroupManagerProvider;
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/applications/{application}/serverGroupManagers")
public class ServerGroupManagerController {
  final List<ServerGroupManagerProvider> serverGroupManagerProviders;

  final RequestQueue requestQueue;

  @Autowired
  public ServerGroupManagerController(List<ServerGroupManagerProvider> serverGroupManagerProviders, RequestQueue requestQueue) {
    this.serverGroupManagerProviders = serverGroupManagerProviders;
    this.requestQueue = requestQueue;
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @PostFilter("hasPermission(filterObject.account, 'ACCOUNT', 'READ')" )
  @RequestMapping(method = RequestMethod.GET)
  Set<ServerGroupManager> getForApplication(@PathVariable String application) {
    return serverGroupManagerProviders.stream()
        .map(provider -> {
          try {
            return requestQueue.execute(application, () -> provider.getServerGroupManagersByApplication(application));
          } catch (Throwable t) {
            log.warn("Failed to read server group managers" , t);
            return null;
          }
        })
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .map(i -> (ServerGroupManager) i)
        .collect(Collectors.toSet());
  }
}
