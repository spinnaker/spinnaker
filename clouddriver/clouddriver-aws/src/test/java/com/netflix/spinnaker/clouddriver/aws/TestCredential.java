/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestCredential {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static NetflixAmazonCredentials named(String name) {
    return named(name, new HashMap<>());
  }

  public static NetflixAmazonCredentials named(String name, Map<String, Object> params) {
    Map<String, Object> credJson = new HashMap<>();
    credJson.put("name", name);
    credJson.put("environment", name);
    credJson.put("accountType", name);
    credJson.put("accountId", "123456789012" + name);
    credJson.put("defaultKeyPair", "default-keypair");
    credJson.put(
        "regions",
        List.of(
            Map.of(
                "name",
                "us-east-1",
                "availabilityZones",
                Arrays.asList("us-east-1b", "us-east-1c", "us-east-1d")),
            Map.of(
                "name",
                "us-west-1",
                "availabilityZones",
                Arrays.asList("us-west-1a", "us-west-1b"))));
    credJson.putAll(params);

    return objectMapper.convertValue(credJson, NetflixAmazonCredentials.class);
  }
}
