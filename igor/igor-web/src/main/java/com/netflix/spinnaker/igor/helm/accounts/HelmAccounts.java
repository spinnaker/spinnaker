/*
 * Copyright 2020 Apple, Inc.
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

package com.netflix.spinnaker.igor.helm.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.igor.helm.model.HelmIndex;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class HelmAccounts {
  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @Autowired HelmAccountsService service;

  public List<HelmAccount> accounts;

  public HelmAccounts() {
    this.accounts = new ArrayList<>();
  }

  public HelmIndex getIndex(String account) {
    // Fetch the index from Clouddriver
    Map<String, String> artifact = new HashMap<>();
    artifact.put("type", "helm/index");
    artifact.put("artifactAccount", account);

    try {
      String yml = Retrofit2SyncCall.execute(service.getIndex(artifact));
      return mapper.readValue(yml, HelmIndex.class);
    } catch (Exception e) {
      log.error("Failed to parse Helm index:", e);
      return null;
    }
  }

  public void updateAccounts() {
    try {
      this.accounts =
          Retrofit2SyncCall.execute(service.getAllAccounts()).stream()
              .filter(it -> it.types.contains("helm/chart"))
              .map(it -> new HelmAccount(it.name))
              .collect(Collectors.toList());
    } catch (SpinnakerServerException e) {
      log.error("Failed to get list of Helm accounts", e);
    }
  }
}
