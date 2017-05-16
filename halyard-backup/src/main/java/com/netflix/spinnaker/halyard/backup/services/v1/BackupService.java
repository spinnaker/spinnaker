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
 *
 */

package com.netflix.spinnaker.halyard.backup.services.v1;

import com.netflix.spinnaker.halyard.backup.kms.v1.SecureStorage;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class BackupService {
  @Autowired
  HalconfigParser halconfigParser;

  @Autowired(required = false)
  SecureStorage secureStorage;

  @Autowired
  HalconfigDirectoryStructure directoryStructure;

  public void create() {
    if (secureStorage == null) {
      // TODO(lwander): point to docs here.
      throw new HalException(Problem.Severity.FATAL, "You must enable secure storage before proceeding.");
    }

    Halconfig halconfig = halconfigParser.getHalconfig();
    halconfig.backupLocalFiles(directoryStructure.getBackupConfigDependenciesPath().toString());
    halconfigParser.backupConfig();

    secureStorage.backupFile("config", directoryStructure.getBackupConfigPath().toFile());
  }
}
