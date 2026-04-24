/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.clouddriver.lambda.cache;

import com.google.common.base.CaseFormat;
import com.netflix.spinnaker.clouddriver.cache.KeyParser;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Keys implements KeyParser {
  // Since we share the same namespace as aws, being an "extension" of AWS,
  // we MUST make sure the namespaced results are unique on the cats system.
  public static final String ID = "aws";

  public enum Namespace {
    IAM_ROLE,
    LAMBDA_FUNCTIONS,
    LAMBDA_APPLICATIONS;

    public final String ns;

    Namespace() {
      ns = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this.name());
    }

    @Override
    public String toString() {
      return ns;
    }
  }

  public static final String SEPARATOR = ":";

  @Override
  public String getCloudProvider() {
    return ID;
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return parse(key);
  }

  @Override
  public Boolean canParseType(String type) {
    return canParse(type);
  }

  private static Boolean canParse(String type) {
    for (Namespace key : Namespace.values()) {
      if (key.toString().equals(type)) {
        return true;
      }
    }
    return false;
  }

  public static Map<String, String> parse(String key) {
    String[] parts = key.split(SEPARATOR);

    if (parts.length < 3 || !ID.equals(parts[0])) {
      return Collections.emptyMap();
    }

    Map<String, String> result = new HashMap<>();
    result.put("provider", ID); // Should be aws for all of these. aka the ID.
    result.put("type", parts[1]); // iamRole or lambdaFunction or application.

    Namespace namespace =
        Namespace.valueOf(CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, parts[1]));

    switch (namespace) {
      case LAMBDA_FUNCTIONS:
        result.put("account", parts[2]); // application name for app, account name for most.
        result.put("region", parts[3]);
        result.put("AwsLambdaName", parts[4]);
        break;
      case IAM_ROLE:
        result.put("account", parts[2]); // application name for app, account name for most.
        result.put("roleName", parts[3]);
        break;
      case LAMBDA_APPLICATIONS:
        result.put("application", parts[2]);
        break;
      default:
        break;
    }

    return result;
  }

  @Override
  public Boolean canParseField(String type) {
    return false;
  }

  public static String getLambdaFunctionKey(String account, String region, String functionName) {
    return String.format(
        "%s:%s:%s:%s:%s", ID, Namespace.LAMBDA_FUNCTIONS, account, region, functionName);
  }

  public static String getIamRoleKey(String account, String iamRoleName) {
    return String.format("%s:%s:%s:%s", ID, Namespace.IAM_ROLE, account, iamRoleName);
  }

  public static String getApplicationKey(String name) {
    return String.format("%s:%s:%s", ID, Namespace.LAMBDA_APPLICATIONS, name.toLowerCase());
  }
}
