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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.security.OAuth2;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import lombok.Data;

@Data
public class SecurityConfig {
  BasicSecurity basic = new BasicSecurity();
  User user = new User();
  OAuth2 oauth2;

  SecurityConfig(ServiceSettings settings) {
    if (settings.getBasicAuthEnabled() == null || settings.getBasicAuthEnabled()) {
      String username = settings.getUsername();
      String password = settings.getPassword();
      assert (username != null && password != null);

      basic.setEnabled(true);
      user.setName(username);
      user.setPassword(password);
    }
  }

  @Data
  public static class BasicSecurity {
    boolean enabled = false;
  }

  @Data
  public static class User {
    String name;
    String password;
  }
}
