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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.error.v1.ConfigNotFoundException;
import com.netflix.spinnaker.halyard.config.error.v1.IllegalConfigException;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStorage;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStore;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.AzsPersistentStore;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.GcsPersistentStore;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.OracleBMCSPersistentStore;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.OraclePersistentStore;
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.S3PersistentStore;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PersistentStorageService {
  @Autowired private LookupService lookupService;

  @Autowired private DeploymentService deploymentService;

  @Autowired private ValidateService validateService;

  public PersistentStorage getPersistentStorage(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setPersistentStorage();

    List<PersistentStorage> matching =
        lookupService.getMatchingNodesOfType(filter, PersistentStorage.class);

    switch (matching.size()) {
      case 0:
        PersistentStorage persistentStorage = new PersistentStorage();
        setPersistentStorage(deploymentName, persistentStorage);
        return persistentStorage;
      case 1:
        return matching.get(0);
      default:
        throw new RuntimeException(
            "It shouldn't be possible to have multiple persistentStorage nodes. This is a bug.");
    }
  }

  public PersistentStore getPersistentStore(String deploymentName, String persistentStoreType) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setPersistentStore(persistentStoreType);

    List<PersistentStore> matching =
        lookupService.getMatchingNodesOfType(filter, PersistentStore.class);

    switch (matching.size()) {
      case 0:
        throw new ConfigNotFoundException(
            new ConfigProblemBuilder(
                    Problem.Severity.FATAL,
                    "No persistent store with name \"" + persistentStoreType + "\" could be found")
                .setRemediation(
                    "Create a new persistent store with name \"" + persistentStoreType + "\"")
                .build());
      case 1:
        return matching.get(0);
      default:
        throw new IllegalConfigException(
            new ConfigProblemBuilder(
                    Problem.Severity.FATAL,
                    "More than one persistent store with name \""
                        + persistentStoreType
                        + "\" found")
                .setRemediation(
                    "Manually delete or rename duplicate persistent stores with name \""
                        + persistentStoreType
                        + "\" in your halconfig file")
                .build());
    }
  }

  public void setPersistentStorage(String deploymentName, PersistentStorage newPersistentStorage) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    deploymentConfiguration.setPersistentStorage(newPersistentStorage);
  }

  public void setPersistentStore(String deploymentName, PersistentStore newPersistentStore) {
    PersistentStorage persistentStorage = getPersistentStorage(deploymentName);
    switch (newPersistentStore.persistentStoreType()) {
      case S3:
        persistentStorage.setS3((S3PersistentStore) newPersistentStore);
        break;
      case GCS:
        persistentStorage.setGcs((GcsPersistentStore) newPersistentStore);
        break;
      case AZS:
        persistentStorage.setAzs((AzsPersistentStore) newPersistentStore);
        break;
      case ORACLE:
        persistentStorage.setOracle((OraclePersistentStore) newPersistentStore);
        break;
      case ORACLEBMCS:
        persistentStorage.setOraclebmcs((OracleBMCSPersistentStore) newPersistentStore);
        break;
      default:
        throw new RuntimeException(
            "Unknown persistent store " + newPersistentStore.persistentStoreType());
    }
  }

  public ProblemSet validatePersistentStorage(String deploymentName) {
    PersistentStorage storage = getPersistentStorage(deploymentName);

    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setPersistentStorage();

    if (storage.getPersistentStoreType() != null) {
      filter.setPersistentStore(storage.getPersistentStoreType().getId());
    }

    return validateService.validateMatchingFilter(filter);
  }

  public ProblemSet validatePersistentStore(String deploymentName, String persistentStoreType) {
    NodeFilter filter =
        new NodeFilter().setDeployment(deploymentName).setPersistentStore(persistentStoreType);

    return validateService.validateMatchingFilter(filter);
  }
}
