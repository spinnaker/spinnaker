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

import com.netflix.spinnaker.halyard.config.config.v1.RelaxedObjectMapper;
import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.RedisPersistentStore;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService.Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class Front50ProfileFactory extends SpringProfileFactory {
  @Autowired
  AccountService accountService;

  @Autowired
  RelaxedObjectMapper objectMapper;

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.FRONT50;
  }

  @Override
  public void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    PersistentStorage persistentStorage = deploymentConfiguration.getPersistentStorage();

    if (persistentStorage.getPersistentStoreType() == null) {
      throw new HalException(Problem.Severity.FATAL, "No persistent storage type was configured.");
    }

    List<String> files = backupRequiredFiles(persistentStorage, deploymentConfiguration.getName());
    Map<String, Map<String, Object>> persistentStorageMap = new HashMap<>();

    NodeIterator children = persistentStorage.getChildren();
    Node child = children.getNext();
    while (child != null) {
      if (child instanceof PersistentStore) {
        PersistentStore persistentStore = (PersistentStore) child;

        URI connectionUri = null;
        if (persistentStore instanceof RedisPersistentStore) {
          try {
            connectionUri = new URI(endpoints.getServiceSettings(Type.REDIS).getBaseUrl());
          } catch (URISyntaxException e) {
            throw new RuntimeException("Malformed redis URL, this is a bug.");
          }
        }

        persistentStore.setConnectionInfo(connectionUri);

        PersistentStore.PersistentStoreType persistentStoreType = persistentStore.persistentStoreType();
        Map persistentStoreMap = objectMapper.convertValue(persistentStore, Map.class);
        persistentStoreMap.put("enabled", persistentStoreType.equals(persistentStorage.getPersistentStoreType()));

        persistentStorageMap.put(persistentStoreType.getId(), persistentStoreMap);
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
