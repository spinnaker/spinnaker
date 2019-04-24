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

package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UnrestrictedResourceConfig {

  public static String UNRESTRICTED_USERNAME = "__unrestricted_user__";

  @Bean
  @ConditionalOnExpression("${fiat.write-mode.enabled:true}")
  String addUnrestrictedUser(PermissionsRepository permissionsRepository) {
    if (!permissionsRepository.get(UNRESTRICTED_USERNAME).isPresent()) {
      permissionsRepository.put(new UserPermission().setId(UNRESTRICTED_USERNAME));
    }
    return UNRESTRICTED_USERNAME;
  }
}
