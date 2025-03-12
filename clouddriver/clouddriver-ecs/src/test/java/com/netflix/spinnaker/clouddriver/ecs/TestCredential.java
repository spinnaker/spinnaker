/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.ecs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import java.util.*;

public class TestCredential {

  public static NetflixAmazonCredentials named(String name) {
    return TestCredential.named(name, Collections.emptyMap());
  }

  public static NetflixAmazonCredentials named(String name, Map<String, Object> additionalParams) {
    final Map<String, Object> params = new LinkedHashMap<>();
    params.put("name", name);
    params.put("environment", name);
    params.put("accountType", name);
    params.put("accountId", "123456789012" + name);
    params.put("defaultKeyPair", "default-keypair");

    final Map<String, Object> region1 = new LinkedHashMap<>();
    region1.put("name", "us-east-1");
    region1.put("availabilityZones", Arrays.asList("us-east-1b", "us-east-1c", "us-east-1d"));

    final Map<String, Object> region2 = new LinkedHashMap<>();
    region2.put("name", "us-west-1");
    region2.put("availabilityZones", Arrays.asList("us-west-1a", "us-west-1b"));

    params.put("regions", Arrays.asList(region1, region2));

    params.putAll(additionalParams);

    return new ObjectMapper().convertValue(params, NetflixAmazonCredentials.class);
  }

  public static NetflixAssumeRoleAmazonCredentials assumeRoleNamed(String name) {
    return TestCredential.assumeRoleNamed(name, Collections.emptyMap());
  }

  public static NetflixAssumeRoleAmazonCredentials assumeRoleNamed(
      String name, Map<String, Object> additionalParams) {
    final Map<String, Object> params = new LinkedHashMap<>();
    params.put("name", name);
    params.put("environment", name);
    params.put("accountType", name);
    params.put("accountId", "123456789012" + name);
    params.put("defaultKeyPair", "default-keypair");

    final Map<String, Object> region1 = new LinkedHashMap<>();
    region1.put("name", "us-east-1");
    region1.put("availabilityZones", Arrays.asList("us-east-1b", "us-east-1c", "us-east-1d"));

    final Map<String, Object> region2 = new LinkedHashMap<>();
    region2.put("name", "us-west-1");
    region2.put("availabilityZones", Arrays.asList("us-west-1a", "us-west-1b"));

    params.put("regions", Arrays.asList(region1, region2));

    params.put("assumeRole", "role/" + name);
    params.put("sessionName", name);
    params.put("externalId", name);

    params.putAll(additionalParams);

    return new ObjectMapper().convertValue(params, NetflixAssumeRoleAmazonCredentials.class);
  }
}
