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

package com.netflix.spinnaker.clouddriver.cloudfoundry.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties("cloudfoundry")
public class CloudFoundryConfigurationProperties implements DisposableBean {
  static final int POLLING_INTERVAL_MILLISECONDS_DEFAULT = 300 * 1000;
  static final int ASYNC_OPERATION_TIMEOUT_MILLISECONDS_DEFAULT =
      (int) (POLLING_INTERVAL_MILLISECONDS_DEFAULT * 1.5);
  static final int ASYNC_OPERATION_MAX_POLLING_INTERVAL_MILLISECONDS = 8 * 1000;

  private int pollingIntervalMilliseconds = POLLING_INTERVAL_MILLISECONDS_DEFAULT;
  private int asyncOperationTimeoutMillisecondsDefault =
      ASYNC_OPERATION_TIMEOUT_MILLISECONDS_DEFAULT;
  private int asyncOperationMaxPollingIntervalMilliseconds =
      ASYNC_OPERATION_MAX_POLLING_INTERVAL_MILLISECONDS;

  private List<ManagedAccount> accounts = new ArrayList<>();

  @Override
  public void destroy() {
    this.accounts = new ArrayList<>();
  }

  @Getter
  @Setter
  @ToString(exclude = "password")
  public static class ManagedAccount {
    private String name;
    private String api;
    private String appsManagerUri;
    private String metricsUri;
    private String user;
    private String password;
    private String environment;
  }
}
