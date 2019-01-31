/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.halyard.config.config.v1.secrets;

import com.netflix.spinnaker.config.secrets.SecretManager;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 *  SecretSession contains the cached decrypted secrets and secret files
 */
class SecretSession {

  @Autowired
  private SecretManager secretManager;

  private Map<String, Path> tempFiles = new HashMap<>();

  void addFile(String encrypted, Path decryptedFilePath) {
    tempFiles.put(encrypted, decryptedFilePath);
  }

  void clearTempFiles() {
    for (String encryptedFilePath : tempFiles.keySet()) {
      secretManager.clearCachedFile(encryptedFilePath);
      File f = new File(tempFiles.get(encryptedFilePath).toString());
      f.delete();
    }
  }
}
