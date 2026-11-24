/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.fiat.model.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.fiat.YamlFileApplicationContextInitializer;
import com.netflix.spinnaker.fiat.model.Authorization;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
    classes = PermissionsTest.TestConfig.class,
    initializers = YamlFileApplicationContextInitializer.class)
public class PermissionsTest {

  private static final Authorization R = Authorization.READ;
  private static final Authorization W = Authorization.WRITE;
  private static final Authorization E = Authorization.EXECUTE;
  private static final Authorization C = Authorization.CREATE;

  @Autowired private TestConfigProps testConfigProps;

  // Make line endings consistent regardless of OS
  private final PrettyPrinter printer =
      new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter().withLinefeed("\n"));

  private final ObjectMapper mapper =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  private final String permissionJson =
      "{\n  \"READ\" : [ \"foo\" ],\n  \"WRITE\" : [ \"bar\" ]\n}";

  private final String permissionSerialized =
      "{\n  \"READ\" : [ \"foo\" ],\n  \"WRITE\" : [ \"bar\" ]\n}";

  @Test
  public void shouldDeserialize() throws Exception {
    Permissions p = mapper.readValue(permissionJson, Permissions.class);

    assertEquals(Set.of("foo"), p.get(R));
    assertEquals(Set.of("bar"), p.get(W));
    assertTrue(p.get(E).isEmpty());

    Permissions.Builder b = mapper.readValue(permissionJson, Permissions.Builder.class);
    p = b.build();

    assertEquals(Set.of("foo"), p.get(R));
    assertEquals(Set.of("bar"), p.get(W));
    assertTrue(p.get(E).isEmpty());
  }

  @Test
  public void shouldSerialize() throws Exception {
    Permissions.Builder b = new Permissions.Builder();
    b.set(Map.of(R, Set.of("foo"), W, Set.of("bar")));

    assertEquals(permissionSerialized, mapper.writer(printer).writeValueAsString(b.build()));
  }

  @Test
  public void canDeserializeToBuilderFromSerializedPermissions() throws Exception {
    Permissions.Builder b1 = new Permissions.Builder().add(W, "batman").add(R, "robin");
    Permissions p1 = b1.build();

    String serialized = mapper.writeValueAsString(p1);
    Permissions.Builder b2 = mapper.readValue(serialized, Permissions.Builder.class);
    Permissions p2 = b2.build();

    assertEquals(p1, p2);
  }

  @Test
  public void shouldTrimAndLower() {
    Permissions.Builder b = new Permissions.Builder();
    b.set(Map.of(R, Set.of("FOO")));

    assertEquals(Set.of("foo"), b.build().get(R));

    b.add(W, "bAr          ");

    assertEquals(Set.of("bar"), b.build().get(W));
  }

  @Test
  public void testImmutability() {
    Permissions.Builder b = new Permissions.Builder().add(R, "foo").add(W, "bar");

    b.add(R, "baz");

    assertEquals(2, b.get(R).size());

    Permissions im = b.build();
    assertThrows(UnsupportedOperationException.class, () -> im.get(R).clear());
  }

  @Test
  public void testAllGroups() {
    Permissions.Builder b = new Permissions.Builder().add(R, "foo");

    assertEquals(Set.of("foo"), b.build().allGroups());

    Permissions p = Permissions.factory(Map.of(R, Set.of("bar"), W, Set.of("bar")));

    assertEquals(Set.of("bar"), p.allGroups());

    p = Permissions.factory(Map.of(R, Set.of("foo"), W, Set.of("bar")));

    assertEquals(Set.of("foo", "bar"), p.allGroups());
  }

  @Test
  public void testIsRestricted() {
    assertFalse(new Permissions.Builder().build().isRestricted());
    assertTrue(new Permissions.Builder().add(R, "foo").build().isRestricted());
  }

  @Test
  public void testGetAuthorizations() {
    Permissions p = new Permissions.Builder().build();

    assertEquals(Set.of(R, W, E, C), p.getAuthorizations(Set.of(new Role())));

    p = Permissions.factory(Map.of(R, Set.of("bar"), W, Set.of("bar")));

    assertEquals(Set.of(R, W), p.getAuthorizations(Set.of(new Role("bar"))));

    p = Permissions.factory(Map.of(R, Set.of("bar")));

    assertEquals(Set.of(R), p.getAuthorizations(Set.of(new Role("bar"), new Role("foo"))));
  }

  @Test
  public void testConfigPropsDeserialization() {
    assertNotNull(testConfigProps);
    assertNotNull(testConfigProps.permissions);

    Permissions p = testConfigProps.permissions.build();

    assertEquals(Set.of("foo"), p.get(R));
    assertEquals(Set.of("bar"), p.get(W));
  }

  @Configuration
  @EnableConfigurationProperties(TestConfigProps.class)
  static class TestConfig {}

  @ConfigurationProperties("test-root")
  public static class TestConfigProps {
    private Permissions.Builder permissions;

    public Permissions.Builder getPermissions() {
      return permissions;
    }

    public void setPermissions(Permissions.Builder permissions) {
      this.permissions = permissions;
    }
  }
}
