/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.clouddriver.tencentcloud.config;

import java.util.List;
import lombok.Data;

@Data
public class TencentCloudConfigurationProperties {

  @Data
  public static class ManagedAccount {
    private String name;
    private String environment;
    private String accountType;
    private String project;
    private String secretId;
    private String secretKey;
    private List<String> regions;
  }

  private List<ManagedAccount> accounts;
}
