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

package com.netflix.spinnaker.kork.jedis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import org.junit.Test;
import redis.clients.jedis.Protocol;

public class RedisClientConnectionPropertiesTest {
  private static final String TEST_SSL_URI = "rediss://somehost.somedomain.net:8379/";
  private static final String TEST_NON_SSL_URI = "redis://somehost.somedomain.net:8379/";
  private static final String TEST_PASSWORD_URI =
      "rediss://admin:S0meP%40ssw0rd@somehost.somedomain.net:8379/";
  private static final String TEST_NO_PASSWORD_URI = "rediss://admin@somehost.somedomain.net:8379/";
  private static final String TEST_CONFIGURED_PORT =
      "rediss://admin:S0meP%40ssw0rd@somehost.somedomain.net:8379/";
  private static final String TEST_DEFAULT_PORT =
      "rediss://admin:S0meP@ssw0rd@somehost.somedomain.net/";
  private static final String TEST_CONFGIURED_DATABASE =
      "rediss://admin:S0meP%40ssw0rd@somehost.somedomain.net/8";
  private static final String TEST_DEFAULT_DATABASE =
      "rediss://admin:S0meP%40ssw0rd@somehost.somedomain.net";

  @Test
  public void getSSLwhenSSLScheme() {
    URI uri = URI.create(TEST_SSL_URI);
    RedisClientConnectionProperties rcp = new RedisClientConnectionProperties(uri);
    assertTrue(rcp.isSSL());
  }

  @Test
  public void getNotSSLwhenNotSSLScheme() {
    URI uri = URI.create(TEST_NON_SSL_URI);
    RedisClientConnectionProperties rcp = new RedisClientConnectionProperties(uri);
    assertFalse(rcp.isSSL());
  }

  @Test
  public void getConfiguredPassword() {
    URI uri = URI.create(TEST_PASSWORD_URI);
    RedisClientConnectionProperties rcp = new RedisClientConnectionProperties(uri);
    assertEquals("S0meP@ssw0rd", rcp.password());
  }

  @Test
  public void getNullWhenNoPassword() {
    URI uri = URI.create(TEST_NO_PASSWORD_URI);
    RedisClientConnectionProperties rcp = new RedisClientConnectionProperties(uri);
    assertNull(rcp.password());
  }

  @Test
  public void getConfiguredPort() {
    URI uri = URI.create(TEST_CONFIGURED_PORT);
    RedisClientConnectionProperties rcp = new RedisClientConnectionProperties(uri);
    assertEquals(8379, rcp.port());
  }

  @Test
  public void getDefaultPort() {
    URI uri = URI.create(TEST_DEFAULT_PORT);
    RedisClientConnectionProperties rcp = new RedisClientConnectionProperties(uri);
    assertEquals(Protocol.DEFAULT_PORT, rcp.port());
  }

  @Test
  public void getConfiguredDatabase() {
    URI uri = URI.create(TEST_CONFGIURED_DATABASE);
    RedisClientConnectionProperties rcp = new RedisClientConnectionProperties(uri);
    assertEquals(8, rcp.database());
  }

  @Test
  public void getDefaultDatabase() {
    URI uri = URI.create(TEST_DEFAULT_DATABASE);
    RedisClientConnectionProperties rcp = new RedisClientConnectionProperties(uri);
    assertEquals(Protocol.DEFAULT_DATABASE, rcp.database());
  }

  @Test
  public void getHost() {
    URI uri = URI.create(TEST_SSL_URI);
    RedisClientConnectionProperties rcp = new RedisClientConnectionProperties(uri);
    assertEquals("somehost.somedomain.net", rcp.addr());
  }
}
