/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.config;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties("fiat")
public class FiatAdminConfig implements InitializingBean {

  private AdminRoles admin = new AdminRoles();

  @Override
  public void afterPropertiesSet() throws Exception {
    admin.roles =
        admin.roles.stream()
            .map(String::toLowerCase)
            .map(String::trim)
            .collect(Collectors.toList());
  }

  @Data
  public static class AdminRoles {
    private List<String> roles = new ArrayList<>();
  }
}
