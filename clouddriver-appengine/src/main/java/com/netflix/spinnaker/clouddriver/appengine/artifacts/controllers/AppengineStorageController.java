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
 */

package com.netflix.spinnaker.clouddriver.appengine.artifacts.controllers;

import com.netflix.spinnaker.clouddriver.appengine.artifacts.config.StorageConfigurationProperties;

import groovy.util.logging.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

// TODO(jacobkiefer): Refactor this Controller into a common controller with injected StorageService(s) when we
// add another storage account service. Leaving this in Appengine's scope for now.
@Slf4j
@RestController
@RequestMapping("/storage")
class AppengineStorageController {

  @Autowired(required = false)
  private StorageConfigurationProperties storageAccountInfo;

  @RequestMapping(method = RequestMethod.GET)
  List<String> list() {
    if (storageAccountInfo == null) {
      return new ArrayList<>();
    }
    List<String> results = new ArrayList<String>(storageAccountInfo.getAccounts().size());
    for (StorageConfigurationProperties.ManagedAccount account : storageAccountInfo.getAccounts()) {
        results.add(account.getName());
    }
    return results;
  }
}
