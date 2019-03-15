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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.netflix.spinnaker.config.secrets.EncryptedSecret;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.validate.v1.util.ValidatingFileReader;
import com.netflix.spinnaker.halyard.core.secrets.v1.SecretSessionManager;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class Validator<T extends Node> {
  @Autowired
  SecretSessionManager secretSessionManager;

  abstract public void validate(ConfigProblemSetBuilder p, T n);

  public String validatingFileDecrypt(ConfigProblemSetBuilder p, String filePath) {
    if (EncryptedSecret.isEncryptedSecret(filePath)) {
      return secretSessionManager.decrypt(filePath);
    } else {
      return ValidatingFileReader.contents(p, filePath);
    }
  }
}
