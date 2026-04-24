/*
 * Copyright 2026 Harness, Inc. or its affiliates.
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

package com.netflix.spinnaker.clouddriver.lambda.names;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class LambdaResourceFunctionTest {

  @Test
  public void shouldGetFunctionName() {
    Map<String, Object> function = new HashMap<>();
    function.put("functionName", "my-function");

    LambdaResourceFunction resource = new LambdaResourceFunction(function);

    assertThat(resource.getName()).isEqualTo("my-function");
  }

  @Test
  public void shouldReturnNullWhenFunctionNameNotSet() {
    Map<String, Object> function = new HashMap<>();

    LambdaResourceFunction resource = new LambdaResourceFunction(function);

    assertThat(resource.getName()).isNull();
  }

  @Test
  public void shouldSetFunctionName() {
    Map<String, Object> function = new HashMap<>();
    LambdaResourceFunction resource = new LambdaResourceFunction(function);

    resource.setName("new-function-name");

    assertThat(function.get("functionName")).isEqualTo("new-function-name");
    assertThat(resource.getName()).isEqualTo("new-function-name");
  }

  @Test
  public void shouldGetResourceTagsWhenTagsExist() {
    Map<String, Object> function = new HashMap<>();
    Map<String, String> tags = new HashMap<>();
    tags.put("Environment", "production");
    tags.put("Team", "backend");
    function.put("tags", tags);

    LambdaResourceFunction resource = new LambdaResourceFunction(function);

    assertThat(resource.getResourceTags()).isEqualTo(tags);
    assertThat(resource.getResourceTags()).containsEntry("Environment", "production");
    assertThat(resource.getResourceTags()).containsEntry("Team", "backend");
  }

  @Test
  public void shouldReturnEmptyMapWhenTagsNotSet() {
    Map<String, Object> function = new HashMap<>();

    LambdaResourceFunction resource = new LambdaResourceFunction(function);

    assertThat(resource.getResourceTags()).isEmpty();
  }

  @Test
  public void shouldReturnEmptyMapWhenTagsIsNotAMap() {
    Map<String, Object> function = new HashMap<>();
    function.put("tags", "not-a-map");

    LambdaResourceFunction resource = new LambdaResourceFunction(function);

    assertThat(resource.getResourceTags()).isEmpty();
  }

  @Test
  public void shouldReturnEmptyMapWhenTagsIsNull() {
    Map<String, Object> function = new HashMap<>();
    function.put("tags", null);

    LambdaResourceFunction resource = new LambdaResourceFunction(function);

    assertThat(resource.getResourceTags()).isEmpty();
  }
}
