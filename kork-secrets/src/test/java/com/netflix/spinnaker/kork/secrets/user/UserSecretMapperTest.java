package com.netflix.spinnaker.kork.secrets.user;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

import com.netflix.spinnaker.kork.secrets.SecretConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = SecretConfiguration.class)
public class UserSecretMapperTest {

  @Autowired UserSecretMapper mapper;

  @Test
  public void jsonStringMap() {
    var bytes =
        mapper.serialize(
            OpaqueUserSecret.builder()
                .roles(List.of("dev", "sre"))
                .stringData(Map.of("foo", "bar", "second", "second"))
                .build(),
            "json");
    var secret = mapper.deserialize(bytes, "json");
    assertThat(secret.getRoles(), contains("dev", "sre"));
    assertThat(secret, instanceOf(OpaqueUserSecret.class));
    assertThat(secret.getSecretString("foo"), equalTo("bar"));
    assertThat(secret.getSecretString("second"), equalTo("second"));
  }

  @Test
  public void jsonBinaryMap() {
    var bytes =
        mapper.serialize(
            OpaqueUserSecret.builder()
                .roles(List.of("foo", "bar"))
                .data(Map.of("first", "second".getBytes(StandardCharsets.UTF_8)))
                .build(),
            "json");
    var secret = mapper.deserialize(bytes, "json");
    assertThat(secret, instanceOf(OpaqueUserSecret.class));
    assertThat(secret.getSecretBytes("first"), equalTo("second".getBytes(StandardCharsets.UTF_8)));
    assertThat(secret.getRoles(), contains("foo", "bar"));
  }

  @Test
  public void yamlStringMap() {
    var bytes =
        mapper.serialize(
            OpaqueUserSecret.builder()
                .roles(List.of("one"))
                .stringData(Map.of("a", "A", "b", "B", "c", "C"))
                .build(),
            "yaml");
    var secret = mapper.deserialize(bytes, "yaml");
    assertThat(secret, instanceOf(OpaqueUserSecret.class));
    assertThat(secret.getRoles(), contains("one"));
    assertThat(secret.getSecretString("a"), equalTo("A"));
    assertThat(secret.getSecretString("b"), equalTo("B"));
    assertThat(secret.getSecretString("c"), equalTo("C"));
  }

  @Test
  public void cborBinaryMap() {
    var binaryData = "packed".getBytes(StandardCharsets.UTF_8);
    var bytes =
        mapper.serialize(
            OpaqueUserSecret.builder()
                .roles(List.of("one", "two", "three"))
                .data(Map.of("bin", binaryData))
                .build(),
            "cbor");
    var secret = mapper.deserialize(bytes, "cbor");
    assertThat(secret, instanceOf(OpaqueUserSecret.class));
    assertThat(secret.getRoles(), contains("one", "two", "three"));
    assertThat(secret.getSecretBytes("bin"), equalTo(binaryData));
  }
}
