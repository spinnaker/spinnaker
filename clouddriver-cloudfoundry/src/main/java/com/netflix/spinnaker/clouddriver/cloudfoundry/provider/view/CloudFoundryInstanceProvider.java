/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view;

import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.Logs;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Task;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.model.InstanceProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CloudFoundryInstanceProvider
    implements InstanceProvider<CloudFoundryInstance, String> {

  private final CacheRepository repository;
  private final AccountCredentialsProvider accountCredentialsProvider;

  @Nullable
  @Override
  public CloudFoundryInstance getInstance(String account, String region, String id) {
    return repository.findInstanceByKey(Keys.getInstanceKey(account, id)).orElse(null);
  }

  @Override
  public String getConsoleOutput(String account, String region, String id) {
    AccountCredentials credentials = accountCredentialsProvider.getCredentials(account);
    if (!(credentials instanceof CloudFoundryCredentials)) {
      return null;
    }
    final CloudFoundryClient client = ((CloudFoundryCredentials) credentials).getClient();
    final Logs logsService = client.getLogs();

    final CloudFoundryConsoleOutputIdParameter idParam =
        CloudFoundryConsoleOutputIdParameter.fromString(id);

    final String logs;
    LogsResourceType logsResourceType = idParam.logsResourceType;
    switch (logsResourceType) {
      case APP:
        logs = logsService.recentApplicationLogs(idParam.guid, idParam.instanceIndex);
        break;
      case TASK:
        Task task = client.getTasks().getTask(idParam.guid);
        String appGuid = task.getLinks().get("app").getGuid();
        logs = logsService.recentTaskLogs(appGuid, task.getName());
        break;
      default:
        throw new IllegalArgumentException("Unsupported LogsResourceType: " + logsResourceType);
    }

    return logs;
  }

  public final String getCloudProvider() {
    return CloudFoundryCloudProvider.ID;
  }

  @RequiredArgsConstructor
  @Data
  static class CloudFoundryConsoleOutputIdParameter {
    private final LogsResourceType logsResourceType;
    private final String guid;
    private final int instanceIndex;

    static CloudFoundryConsoleOutputIdParameter fromString(String value) {
      try {
        String[] parts = value.split(":");
        LogsResourceType type = LogsResourceType.valueOf(parts[0].toUpperCase());
        return new CloudFoundryConsoleOutputIdParameter(
            type, parts[1], type == LogsResourceType.APP ? Integer.parseInt(parts[2]) : 0);
      } catch (Exception e) {
        throw new IllegalArgumentException(
            String.format(
                "Error parsing '%s'. Expected format: 'app:appGuid:instanceIndex' or 'task:taskGuid'",
                value),
            e);
      }
    }
  }

  enum LogsResourceType {
    APP,
    TASK
  }
}
