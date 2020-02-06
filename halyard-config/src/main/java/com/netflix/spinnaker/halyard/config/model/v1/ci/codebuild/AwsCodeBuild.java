/*
 * Copyright 2020 Amazon.com, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.ci.codebuild;

import com.netflix.spinnaker.halyard.config.model.v1.node.Ci;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class AwsCodeBuild extends Ci<AwsCodeBuildAccount> {
  private boolean enabled;
  private List<AwsCodeBuildAccount> accounts = new ArrayList<>();

  public List<AwsCodeBuildAccount> listAccounts() {
    return accounts;
  }

  @Override
  public String getNodeName() {
    return "codebuild";
  }
}
