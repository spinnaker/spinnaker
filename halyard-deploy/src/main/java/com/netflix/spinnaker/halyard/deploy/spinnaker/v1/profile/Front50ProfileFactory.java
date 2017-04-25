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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.GcsPersistentStore;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.CommonGoogleAccount;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class Front50ProfileFactory extends SpringProfileFactory {
  @Autowired
  AccountService accountService;

  @Autowired
  ObjectMapper objectMapper;

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.FRONT50;
  }

  @Override
  public void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    PersistentStorage persistentStorage = deploymentConfiguration.getPersistentStorage();
    List<String> files = processRequiredFiles(persistentStorage);
    Map persistentStorageMap = objectMapper.convertValue(persistentStorage, Map.class);
    persistentStorageMap.remove("persistentStoreType");

    NodeIterator children = persistentStorage.getChildren();
    Node child = children.getNext();
    while (child != null) {
      if (child instanceof PersistentStore) {
        PersistentStore persistentStore = (PersistentStore) child;

        PersistentStore.PersistentStoreType persistentStoreType = persistentStore.persistentStoreType();
        Map persistentStoreMap = (Map) persistentStorageMap.get(persistentStoreType.getId());
        persistentStoreMap.put("enabled", persistentStoreType.getId().equalsIgnoreCase(persistentStorage.getPersistentStoreType()));
      }

      child = children.getNext();
    }

    Map<String, Object> spinnakerObjectMap = new HashMap<>();
    spinnakerObjectMap.put("spinnaker", persistentStorageMap);

    super.setProfile(profile, deploymentConfiguration, endpoints);
    profile.appendContents(yamlToString(spinnakerObjectMap))
        .appendContents(profile.getBaseContents())
        .setRequiredFiles(files);
  }
}
