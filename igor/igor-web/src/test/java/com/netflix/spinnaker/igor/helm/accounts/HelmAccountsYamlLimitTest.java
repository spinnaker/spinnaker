/*
 * Copyright 2024 Netflix, Inc.
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

package com.netflix.spinnaker.igor.helm.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.igor.helm.model.HelmIndex;
import com.netflix.spinnaker.kork.yaml.YamlHelper;
import com.netflix.spinnaker.kork.yaml.YamlParserProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import retrofit2.mock.Calls;

/**
 * Verifies that HelmAccounts respects the YAML code-point limit configured via the injected
 * YamlHelper, allowing operators to control maximum YAML document size when fetching Helm index
 * files.
 */
class HelmAccountsYamlLimitTest {

  private static final int CODE_POINT_LIMIT = 200;

  private HelmAccountsService mockService;
  private HelmAccounts helmAccounts;

  private HelmAccounts newHelmAccounts(int codePointLimit) {
    YamlParserProperties props = new YamlParserProperties();
    props.setCodePointLimit(codePointLimit);
    HelmAccounts accounts = new HelmAccounts(new YamlHelper(props));
    ReflectionTestUtils.setField(accounts, "service", mockService);
    return accounts;
  }

  @BeforeEach
  void setUp() {
    mockService = mock(HelmAccountsService.class);
    helmAccounts = newHelmAccounts(CODE_POINT_LIMIT);
  }

  @Test
  void getIndexReturnsNullWhenYamlExceedsCodePointLimit() {
    String largeYaml = "value: " + "a".repeat(CODE_POINT_LIMIT + 50);
    when(mockService.getIndex(anyMap())).thenReturn(Calls.response(largeYaml));

    HelmIndex result = helmAccounts.getIndex("my-account");

    assertThat(result).isNull();
  }

  // We actually intentionally use a large number to make sure we go bigger than the "default"
  // limits on this ONE test
  @Test
  void getIndexReturnsWhenCodeLimitSetHigher() {
    helmAccounts = newHelmAccounts(CODE_POINT_LIMIT + 6000000);

    String largeYaml = "value: " + "a".repeat(CODE_POINT_LIMIT + 5000000);
    when(mockService.getIndex(anyMap())).thenReturn(Calls.response(largeYaml));

    HelmIndex result = helmAccounts.getIndex("my-account");

    assertThat(result).isNotNull();
  }

  @Test
  void getIndexSucceedsWhenYamlIsWithinCodePointLimit() {
    String validYaml = "apiVersion: v1\nentries: {}\n";
    when(mockService.getIndex(anyMap())).thenReturn(Calls.response(validYaml));

    HelmIndex result = helmAccounts.getIndex("my-account");

    assertThat(result).isNotNull();
    assertThat(result.apiVersion).isEqualTo("v1");
  }
}
