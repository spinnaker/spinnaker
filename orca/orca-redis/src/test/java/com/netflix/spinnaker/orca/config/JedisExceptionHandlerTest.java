/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.orca.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.orca.exceptions.ExceptionHandler;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

class JedisExceptionHandlerTest {

  private final JedisExceptionHandler handler = new JedisExceptionHandler();

  @Test
  void handlesJedisConnectionException() {
    var ex = new JedisConnectionException("Read timed out");
    assertThat(handler.handles(ex)).isTrue();
  }

  @Test
  void handlesJedisException() {
    var ex = new JedisException("Connection reset");
    assertThat(handler.handles(ex)).isTrue();
  }

  @Test
  void handlesWrappedJedisExceptionInCauseChain() {
    var jedisEx = new JedisConnectionException("Read timed out");
    var wrapper = new RuntimeException("Queue push failed", jedisEx);
    assertThat(handler.handles(wrapper)).isTrue();
  }

  @Test
  void doesNotHandleUnrelatedExceptions() {
    assertThat(handler.handles(new IllegalArgumentException("bad arg"))).isFalse();
    assertThat(handler.handles(new NullPointerException("null"))).isFalse();
    assertThat(handler.handles(new RuntimeException("generic"))).isFalse();
  }

  @Test
  void doesNotHandleStandaloneSocketTimeoutException() {
    var ex = new RuntimeException("http timeout", new SocketTimeoutException("Read timed out"));
    assertThat(handler.handles(ex)).isFalse();
  }

  @Test
  void handleReturnsShouldRetryTrue() {
    var ex = new JedisConnectionException("Read timed out");
    ExceptionHandler.Response response = handler.handle("checkPrecondition", ex);

    assertThat(response.isShouldRetry()).isTrue();
    assertThat(response.getExceptionType()).isEqualTo("JedisConnectionException");
    assertThat(response.getOperation()).isEqualTo("checkPrecondition");
    assertThat(response.getDetails()).containsKey("error");
    assertThat(response.getDetails()).containsKey("stackTrace");
  }
}
