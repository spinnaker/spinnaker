/*
 * Copyright 2022 OpsMx Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.cloudrun.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunJobExecutor;
import com.netflix.spinnaker.clouddriver.googlecommon.config.GoogleCommonManagedAccount;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.util.StringUtils;

@Data
public class CloudrunConfigurationProperties {
  private List<ManagedAccount> accounts = new ArrayList<>();
  private String gcloudPath;

  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class ManagedAccount extends GoogleCommonManagedAccount {

    private String serviceAccountEmail;
    private String localRepositoryDirectory = "/tmp";
    private boolean sshTrustUnknownHosts;

    public void initialize(CloudrunJobExecutor jobExecutor, String gcloudPath) {
      if (!StringUtils.isEmpty(getJsonPath())) {
        jobExecutor.runCommand(
            List.of(gcloudPath, "auth", "activate-service-account", "--key-file", getJsonPath()));
        ObjectMapper mapper = new ObjectMapper();
        try {
          JsonNode node = mapper.readTree(new File(getJsonPath()));
          if (StringUtils.isEmpty(getProject())) {
            setProject(node.get("project_id").asText());
          }
        } catch (Exception e) {
          throw new RuntimeException("Could not find read JSON configuration file.", e);
        }
      }
    }
  }
}
