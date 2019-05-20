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

package com.netflix.spinnaker.kork.secrets;

import lombok.Getter;
import lombok.Setter;
import org.springframework.core.env.EnumerablePropertySource;

public class SecretAwarePropertySource extends EnumerablePropertySource<EnumerablePropertySource> {
  @Setter @Getter private SecretManager secretManager;

  public SecretAwarePropertySource(EnumerablePropertySource source, SecretManager secretManager) {
    super(source.getName(), source);
    this.secretManager = secretManager;
  }

  @Override
  public Object getProperty(String name) {
    Object o = source.getProperty(name);
    if (o instanceof String && EncryptedSecret.isEncryptedSecret((String) o)) {
      if (secretManager == null) {
        throw new SecretException("No secret manager to decrypt value of " + name);
      }
      String lName = name.toLowerCase();
      if (lName.endsWith("file") || lName.endsWith("path") || lName.endsWith("truststore")) {
        return secretManager.decryptAsFile((String) o).toString();
      } else if (lName.endsWith("keystore")) {
        return "file:" + secretManager.decryptAsFile((String) o).toString();
      } else {
        return secretManager.decrypt((String) o);
      }
    }
    return o;
  }

  @Override
  public String[] getPropertyNames() {
    return this.source.getPropertyNames();
  }
}
