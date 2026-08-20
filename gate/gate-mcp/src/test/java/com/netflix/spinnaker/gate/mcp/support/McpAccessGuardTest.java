/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.gate.mcp.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.spinnaker.gate.mcp.config.McpServerProperties;
import org.junit.jupiter.api.Test;

class McpAccessGuardTest {

  @Test
  void rejectsWhenReadOnly() {
    McpServerProperties properties = new McpServerProperties();
    properties.setReadOnly(true);
    McpAccessGuard guard = new McpAccessGuard(properties);

    assertThatThrownBy(() -> guard.requireWriteAccess("delete_application"))
        .isInstanceOf(McpReadOnlyModeException.class)
        .hasMessageContaining("delete_application")
        .hasMessageContaining("read-only");
  }

  @Test
  void allowsWhenNotReadOnly() {
    McpServerProperties properties = new McpServerProperties();
    properties.setReadOnly(false);
    McpAccessGuard guard = new McpAccessGuard(properties);

    assertThatCode(() -> guard.requireWriteAccess("delete_application")).doesNotThrowAnyException();
  }
}
