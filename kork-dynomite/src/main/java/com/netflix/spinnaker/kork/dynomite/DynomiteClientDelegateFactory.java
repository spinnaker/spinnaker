/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.kork.dynomite;

import static com.netflix.spinnaker.kork.jedis.RedisClientConfiguration.Driver.DYNOMITE;
import static java.lang.String.format;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.spinnaker.kork.jedis.RedisClientConfiguration.Driver;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegateFactory;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynomiteClientDelegateFactory
    implements RedisClientDelegateFactory<DynomiteClientDelegate> {

  private static final Logger log = LoggerFactory.getLogger(DynomiteClientDelegateFactory.class);

  private ObjectMapper objectMapper;
  private Optional<DiscoveryClient> discoveryClient;

  public DynomiteClientDelegateFactory(
      ObjectMapper objectMapper, Optional<DiscoveryClient> discoveryClient) {
    this.objectMapper = objectMapper;
    this.discoveryClient = discoveryClient;
  }

  @Override
  public boolean supports(Driver driver) {
    return driver == DYNOMITE;
  }

  @Override
  public DynomiteClientDelegate build(String name, Map<String, Object> properties) {
    DynomiteDriverProperties props = convertSpringProperties(properties);
    return new DynomiteClientDelegate(
        name,
        new DynomiteClientFactory().properties(props).discoveryClient(discoveryClient).build());
  }

  /**
   * Spring config parsing is v. dumb. It will start making any iterable a map after a certain
   * depth, so this method massages the data into something we can actually use.
   */
  @SuppressWarnings("unchecked")
  private DynomiteDriverProperties convertSpringProperties(Map<String, Object> properties) {
    Map<String, Object> props = new HashMap<>(properties);

    Map<String, Object> springHosts = (Map<String, Object>) properties.get("hosts");
    if (springHosts != null) {
      props.put("hosts", new ArrayList<>(springHosts.values()));
    }

    ObjectMapper mapper = objectMapper.copy();
    SimpleModule simpleModule = new SimpleModule();
    simpleModule.addDeserializer(
        ConnectionPoolConfigurationImpl.class,
        new ConnectionPoolConfigurationImplDeserializer((String) props.get("applicationName")));
    mapper.registerModule(simpleModule);

    return mapper.convertValue(props, DynomiteDriverProperties.class);
  }

  private static class ConnectionPoolConfigurationImplDeserializer
      extends JsonDeserializer<ConnectionPoolConfigurationImpl> {

    private final String name;

    ConnectionPoolConfigurationImplDeserializer(String name) {
      this.name = name;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ConnectionPoolConfigurationImpl deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException, IllegalMappingMethodAccess {
      ConnectionPoolConfigurationImpl result = new ConnectionPoolConfigurationImpl(name);

      Map<String, Object> raw = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      raw.putAll(ctxt.readValue(p, Map.class));

      Arrays.stream(ConnectionPoolConfigurationImpl.class.getDeclaredMethods())
          .filter(it -> it.getName().startsWith("set"))
          .forEach(
              method -> {
                String fieldName = method.getName().substring(3);
                Object value = raw.get(fieldName);
                if (value != null) {
                  try {
                    method.setAccessible(true);
                    method.invoke(result, value);
                  } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalMappingMethodAccess(
                        format("Could not invoke %s", method.getName()), e);
                  }
                }
              });

      return result;
    }
  }

  private static class IllegalMappingMethodAccess extends RuntimeException {
    IllegalMappingMethodAccess(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
