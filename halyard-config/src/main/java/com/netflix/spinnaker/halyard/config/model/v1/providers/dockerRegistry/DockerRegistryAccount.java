/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry;

import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIteratorFactory;
import com.netflix.spinnaker.halyard.config.model.v1.problem.ProblemSetBuilder;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class DockerRegistryAccount extends Account {
  private String address;
  private String username;
  private String password;
  private String passwordFile;
  private String dockerconfigFile;
  private String email;
  private List<String> repositories = new ArrayList<>();

  public String getAddress() {
    if (address.startsWith("https://") || address.startsWith("http://")) {
      return address;
    } else {
      return "https://" + address;
    }
  }

  @Override
  public void accept(ProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }
}
