/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.igor.docker.model;

import com.netflix.spinnaker.igor.docker.service.ClouddriverService;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class DockerRegistryAccounts {
  @Autowired
  private ClouddriverService service;

  private List<Map> accounts;

  public DockerRegistryAccounts() {
    this.accounts = new ArrayList<>();
  }

  public void updateAccounts() {
    try {
      AuthenticatedRequest.allowAnonymous(() -> {
        this.accounts = Retrofit2SyncCall.execute(service.allAccounts()).stream()
          .filter(it -> "dockerRegistry".equals(it.get("cloudProvider")))
          .map(it -> (String) it.get("name"))
          .map(name -> Retrofit2SyncCall.execute(service.getAccountDetails(name)))
          .collect(Collectors.toList());
        return null;
      });
    } catch (SpinnakerServerException e) {
      log.error("Failed to get list of docker accounts", e);
    }
  }

  public List<Map> getAccounts() {
    return accounts;
  }

  public void setAccounts(List<Map> accounts) {
    this.accounts = accounts;
  }
}
