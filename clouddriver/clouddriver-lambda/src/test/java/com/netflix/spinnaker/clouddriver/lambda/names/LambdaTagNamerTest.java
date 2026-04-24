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

import com.netflix.spinnaker.moniker.Moniker;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class LambdaTagNamerTest {

  @Test
  public void shouldDeriveFromFunctionNameWhenNoTags() {
    LambdaTagNamer namer = new LambdaTagNamer();
    Map<String, Object> function = new HashMap<>();
    function.put("functionName", "myapp-stack-detail-v001");

    LambdaResourceFunction resource = new LambdaResourceFunction(function);
    Moniker moniker = namer.deriveMoniker(resource);

    assertThat(moniker.getApp()).isEqualTo("myapp");
    assertThat(moniker.getStack()).isEqualTo("stack");
    assertThat(moniker.getDetail()).isEqualTo("detail");
    assertThat(moniker.getCluster()).isEqualTo("myapp-stack-detail");
    assertThat(moniker.getSequence()).isEqualTo(1);
  }

  @Test
  public void shouldDeriveFromTagsOverridingFunctionName() {
    LambdaTagNamer namer = new LambdaTagNamer();
    Map<String, Object> function = new HashMap<>();
    function.put("functionName", "some-random-function-name");

    Map<String, String> tags = new HashMap<>();
    tags.put("moniker.spinnaker.io/application", "tagapp");
    tags.put("moniker.spinnaker.io/stack", "tagstack");
    tags.put("moniker.spinnaker.io/detail", "tagdetail");
    function.put("tags", tags);

    LambdaResourceFunction resource = new LambdaResourceFunction(function);
    Moniker moniker = namer.deriveMoniker(resource);

    assertThat(moniker.getApp()).isEqualTo("tagapp");
    assertThat(moniker.getStack()).isEqualTo("tagstack");
    assertThat(moniker.getDetail()).isEqualTo("tagdetail");
    assertThat(moniker.getCluster()).isEqualTo("tagapp-tagstack-tagdetail");
  }

  @Test
  public void shouldUseTagApplicationWithParsedNameDetails() {
    LambdaTagNamer namer = new LambdaTagNamer();
    Map<String, Object> function = new HashMap<>();
    function.put("functionName", "oldapp-stack-detail-v001");

    Map<String, String> tags = new HashMap<>();
    tags.put("moniker.spinnaker.io/application", "newapp");
    function.put("tags", tags);

    LambdaResourceFunction resource = new LambdaResourceFunction(function);
    Moniker moniker = namer.deriveMoniker(resource);

    assertThat(moniker.getApp()).isEqualTo("newapp");
    // Stack and detail should fall back to parsed values
    assertThat(moniker.getStack()).isEqualTo("stack");
    assertThat(moniker.getDetail()).isEqualTo("detail");
    assertThat(moniker.getCluster()).isEqualTo("newapp-stack-detail");
    assertThat(moniker.getSequence()).isEqualTo(1);
  }

  @Test
  public void shouldHandleExplicitClusterTag() {
    LambdaTagNamer namer = new LambdaTagNamer();
    Map<String, Object> function = new HashMap<>();
    function.put("functionName", "myapp-function");

    Map<String, String> tags = new HashMap<>();
    tags.put("moniker.spinnaker.io/application", "myapp");
    tags.put("moniker.spinnaker.io/cluster", "custom-cluster-name");
    function.put("tags", tags);

    LambdaResourceFunction resource = new LambdaResourceFunction(function);
    Moniker moniker = namer.deriveMoniker(resource);

    assertThat(moniker.getApp()).isEqualTo("myapp");
    assertThat(moniker.getCluster()).isEqualTo("custom-cluster-name");
  }

  @Test
  public void shouldHandleFunctionWithoutFriggaFormat() {
    LambdaTagNamer namer = new LambdaTagNamer();
    Map<String, Object> function = new HashMap<>();
    function.put("functionName", "randomfunctionname");

    Map<String, String> tags = new HashMap<>();
    tags.put("moniker.spinnaker.io/application", "myapp");
    function.put("tags", tags);

    LambdaResourceFunction resource = new LambdaResourceFunction(function);
    Moniker moniker = namer.deriveMoniker(resource);

    assertThat(moniker.getApp()).isEqualTo("myapp");
    assertThat(moniker.getCluster()).isEqualTo("myapp");
  }
}
