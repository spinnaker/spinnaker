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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.test.util.ReflectionTestUtils;
import retrofit2.mock.Calls;

/**
 * Verifies that HelmAccounts respects the YAML code-point limit configured via YamlHelper, allowing
 * operators to control maximum YAML document size when fetching Helm index files.
 */
@Execution(
    ExecutionMode
        .SAME_THREAD) // Since we're "tweaking" the size limits... and those are static... can't run
// concurrently
class HelmAccountsYamlLimitTest {

  private static final int CODE_POINT_LIMIT = 200;

  private HelmAccountsService mockService;
  private HelmAccounts helmAccounts;

  @BeforeEach
  void setUp() {
    YamlParserProperties props = new YamlParserProperties();
    props.setCodePointLimit(CODE_POINT_LIMIT);
    new YamlHelper(props);

    helmAccounts = new HelmAccounts();
    mockService = mock(HelmAccountsService.class);
    ReflectionTestUtils.setField(helmAccounts, "service", mockService);
  }

  @AfterEach
  void tearDown() {
    // Reset YamlHelper state so this test does not affect other tests
    new YamlHelper(new YamlParserProperties());
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
    YamlParserProperties props = new YamlParserProperties();
    props.setCodePointLimit(CODE_POINT_LIMIT + 6000000);
    new YamlHelper(props);
    helmAccounts = new HelmAccounts();
    mockService = mock(HelmAccountsService.class);
    ReflectionTestUtils.setField(helmAccounts, "service", mockService);

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
