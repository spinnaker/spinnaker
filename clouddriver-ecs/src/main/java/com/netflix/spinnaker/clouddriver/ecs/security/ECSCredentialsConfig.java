/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.security;

import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import java.util.List;
import lombok.Data;

@Data
public class ECSCredentialsConfig {
  String defaultNamingStrategy = "default";
  List<Account> accounts;

  @Data
  public static class Account implements CredentialsDefinition {
    private String name;
    private String awsAccount;
    private String namingStrategy;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Account account = (Account) o;

      if (!name.equals(account.name)) return false;
      return awsAccount.equals(account.awsAccount);
    }

    @Override
    public int hashCode() {
      int result = name.hashCode();
      result = 31 * result + awsAccount.hashCode();
      return result;
    }
  }
}
