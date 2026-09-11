/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.kork.aws;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spectator.aws2.SpectatorExecutionInterceptor;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.interceptor.ClasspathInterceptorChainFactory;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

/**
 * Verifies that {@code software/amazon/awssdk/global/handlers/execution.interceptors} actually
 * makes {@link SpectatorExecutionInterceptor} discoverable through the AWS SDK v2's own global
 * interceptor-loading mechanism -- the same mechanism {@code SdkDefaultClientBuilder} uses to
 * attach interceptors to every v2 client built anywhere on this classpath, with no per-client
 * wiring required.
 */
class GlobalExecutionInterceptorTest {

  @Test
  void spectatorExecutionInterceptorIsDiscoveredGlobally() {
    List<ExecutionInterceptor> globalInterceptors =
        new ClasspathInterceptorChainFactory().getGlobalInterceptors();

    assertTrue(
        globalInterceptors.stream().anyMatch(i -> i instanceof SpectatorExecutionInterceptor),
        "software/amazon/awssdk/global/handlers/execution.interceptors should make"
            + " SpectatorExecutionInterceptor discoverable without any per-client wiring");
  }
}
