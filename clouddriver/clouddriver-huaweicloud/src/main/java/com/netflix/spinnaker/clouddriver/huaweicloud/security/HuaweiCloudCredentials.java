/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.huaweicloud.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.huawei.openstack4j.api.OSClient;
import com.huawei.openstack4j.core.transport.Config;
import com.huawei.openstack4j.model.common.Identifier;
import com.huawei.openstack4j.model.identity.v3.Token;
import com.huawei.openstack4j.openstack.OSFactory;
import com.netflix.spinnaker.clouddriver.huaweicloud.client.AuthorizedClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HuaweiCloudCredentials implements AuthorizedClientProvider {
  private final Logger log = LoggerFactory.getLogger(HuaweiCloudCredentials.class);

  private final String authUrl;
  private final String username;
  @JsonIgnore private final String password;
  private final String projectName;
  private final String domainName;
  private final Boolean insecure;

  @JsonIgnore private Token token = null;

  public HuaweiCloudCredentials(
      String authUrl,
      String username,
      String password,
      String projectName,
      String domainName,
      Boolean insecure) {
    this.authUrl = authUrl;
    this.username = username;
    this.password = password;
    this.projectName = projectName;
    this.domainName = domainName;
    this.insecure = insecure;
  }

  public OSClient getAuthClient() {
    Config config =
        insecure ? Config.newConfig().withSSLVerificationDisabled() : Config.newConfig();
    OSClient client = null;
    try {
      if (needRefreshToken()) {
        synchronized (this) {
          if (needRefreshToken()) {
            token =
                OSFactory.builderV3()
                    .withConfig(config)
                    .endpoint(authUrl)
                    .credentials(username, password, Identifier.byName(domainName))
                    .scopeToProject(Identifier.byName(projectName), Identifier.byName(domainName))
                    .authenticate()
                    .getToken();
          }
        }
      }

      client = OSFactory.clientFromToken(token, config);
    } catch (Exception e) {
      log.error("Error building authorized client, error=%s", e);
    }
    return client;
  }

  private boolean needRefreshToken() {
    if (token == null) {
      return true;
    }

    long now = System.currentTimeMillis();
    long expires = token.getExpires().getTime();
    return now >= expires;
  }
}
