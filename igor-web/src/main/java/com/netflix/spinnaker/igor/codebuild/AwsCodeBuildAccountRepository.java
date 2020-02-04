/*
 * Copyright 2020 Amazon.com, Inc.
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

package com.netflix.spinnaker.igor.codebuild;

import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AwsCodeBuildAccountRepository {
  private final Map<String, AwsCodeBuildAccount> accounts = new HashMap<>();

  public void addAccount(String name, AwsCodeBuildAccount service) {
    accounts.put(name, service);
  }

  public AwsCodeBuildAccount getAccount(String name) {
    AwsCodeBuildAccount account = accounts.get(name);
    if (account == null) {
      throw new NotFoundException(
          String.format("No AWS CodeBuild account with name %s is configured", name));
    }
    return account;
  }

  public List<String> getAccountNames() {
    return accounts.keySet().stream().sorted().collect(Collectors.toList());
  }
}
