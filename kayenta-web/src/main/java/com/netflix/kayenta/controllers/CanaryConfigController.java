/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.controllers;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/canaryConfig")
@Slf4j
public class CanaryConfigController {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  StorageServiceRepository storageServiceRepository;

  @RequestMapping(method = RequestMethod.GET)
  public CanaryConfig loadCanaryConfig(@RequestParam(required = false) final String accountName,
                                       @RequestParam String canaryConfigUUID) {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    Optional<StorageService> storageService = storageServiceRepository.getOne(resolvedAccountName);

    if (storageService.isPresent()) {
      return storageService.get().loadObject(resolvedAccountName, ObjectType.CANARY_CONFIG, canaryConfigUUID);
    } else {
      log.debug("No storage service was configured; skipping placeholder logic to read from bucket.");
      return null;
    }
  }

  @RequestMapping(consumes = "application/context+json", method = RequestMethod.POST)
  public String storeCanaryConfig(@RequestParam(required = false) final String accountName,
                                  @RequestBody CanaryConfig canaryConfig) throws IOException {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    Optional<StorageService> storageService = storageServiceRepository.getOne(resolvedAccountName);
    String canaryConfigId = UUID.randomUUID() + "";

    if (storageService.isPresent()) {
      storageService.get().storeObject(resolvedAccountName, ObjectType.CANARY_CONFIG, canaryConfigId, canaryConfig);
    } else {
      log.debug("No storage service was configured; skipping placeholder logic to write to bucket.");
    }

    return canaryConfigId;
  }
}
