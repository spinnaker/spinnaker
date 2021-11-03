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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import com.fasterxml.jackson.annotation.JsonInclude;
import javax.annotation.Nullable;
import lombok.*;

@Data
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class Process {
  private String type;
  private String guid;
  private int instances;
  private int memoryInMb;
  private int diskInMb;

  @Nullable
  @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = HealthCheck.class)
  private HealthCheck healthCheck;

  @Data
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  public static class HealthCheck {

    private HealthCheck() {}

    @Nullable private String type;

    @Nullable private HealthCheckData data;

    public static class HealthCheckBuilder {
      private String type;
      private HealthCheckData data;

      public HealthCheckBuilder type(String type) {
        this.type = type;
        return this;
      }

      public HealthCheckBuilder data(HealthCheckData data) {
        this.data = data;
        return this;
      }

      public HealthCheck build() {
        HealthCheck healthCheck = new HealthCheck();
        healthCheck.setType(this.type);
        healthCheck.setData(this.data);
        return healthCheck;
      }
    }
  }

  @Data
  @JsonInclude(value = JsonInclude.Include.NON_NULL)
  public static class HealthCheckData {

    private HealthCheckData() {}

    @Nullable private String endpoint;

    @Nullable private Integer timeout;

    @Nullable private Integer invocationTimeout;

    public static class HealthCheckDataBuilder {
      private String endpoint;

      private Integer timeout;

      private Integer invocationTimeout;

      public HealthCheckDataBuilder endpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
      }

      public HealthCheckDataBuilder timeout(Integer timeout) {
        this.timeout = timeout;
        return this;
      }

      public HealthCheckDataBuilder invocationTimeout(Integer invocationTimeout) {
        this.invocationTimeout = invocationTimeout;
        return this;
      }

      public HealthCheckData build() {
        HealthCheckData healthCheckData = new HealthCheckData();
        healthCheckData.setEndpoint(this.endpoint);
        healthCheckData.setTimeout(this.timeout);
        healthCheckData.setInvocationTimeout(this.invocationTimeout);
        return healthCheckData;
      }
    }
  }
}
