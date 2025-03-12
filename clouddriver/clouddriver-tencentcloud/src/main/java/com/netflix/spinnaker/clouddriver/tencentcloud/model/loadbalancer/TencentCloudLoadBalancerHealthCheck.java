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

package com.netflix.spinnaker.clouddriver.tencentcloud.model.loadbalancer;

import lombok.Data;

@Data
public class TencentCloudLoadBalancerHealthCheck {

  private Integer healthSwitch;
  private Integer timeOut;
  private Integer intervalTime;
  private Integer healthNum;
  private Integer unHealthNum;
  private Integer httpCode;
  private String httpCheckPath;
  private String httpCheckDomain;
  private String httpCheckMethod;
}
