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

package com.netflix.spinnaker.halyard.config.model.v1.providers.aws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"providerVersion"})
public class AwsAccount extends Account {
  private String defaultKeyPair;
  private String edda;
  private String discovery;
  private String accountId;
  private List<AwsProvider.AwsRegion> regions = new ArrayList<>();
  private String assumeRole;
  private String externalId;
  private String sessionName;
  private List<AwsProvider.AwsLifecycleHook> lifecycleHooks = new ArrayList<>();
  private Boolean lambdaEnabled;
}
