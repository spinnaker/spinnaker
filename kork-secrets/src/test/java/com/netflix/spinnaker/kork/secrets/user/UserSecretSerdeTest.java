/*
 * Copyright 2022 Apple Inc.
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

package com.netflix.spinnaker.kork.secrets.user;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.netflix.spinnaker.kork.secrets.SecretConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = SecretConfiguration.class)
public class UserSecretSerdeTest {

  @Autowired UserSecretSerdeFactory factory;

  @Test
  public void jsonStringMap() {
    var metadata =
        UserSecretMetadata.builder().type("opaque").encoding("json").roles(List.of()).build();
    var serde = factory.serdeFor(metadata);
    var bytes =
        serde.serialize(
            new OpaqueUserSecretData(Map.of("foo", "bar", "second", "second")), metadata);
    var secret = serde.deserialize(bytes, metadata);
    assertThat(secret.getType(), equalTo("opaque"));
    assertThat(secret.getEncoding(), equalTo("json"));
    assertThat(secret.getSecretString("foo"), equalTo("bar"));
    assertThat(secret.getSecretString("second"), equalTo("second"));
  }

  @Test
  public void yamlStringMap() {
    var metadata =
        UserSecretMetadata.builder().type("opaque").encoding("yaml").roles(List.of()).build();
    var serde = factory.serdeFor(metadata);
    var bytes =
        serde.serialize(new OpaqueUserSecretData(Map.of("a", "A", "b", "B", "c", "C")), metadata);
    var secret = serde.deserialize(bytes, metadata);
    assertThat(secret.getType(), equalTo("opaque"));
    assertThat(secret.getEncoding(), equalTo("yaml"));
    assertThat(secret.getSecretString("a"), equalTo("A"));
    assertThat(secret.getSecretString("b"), equalTo("B"));
    assertThat(secret.getSecretString("c"), equalTo("C"));
  }

  @Test
  public void cborStringMap() {
    var metadata =
        UserSecretMetadata.builder().type("opaque").encoding("cbor").roles(List.of()).build();
    var serde = factory.serdeFor(metadata);
    var bytes = serde.serialize(new OpaqueUserSecretData(Map.of("bin", "packed")), metadata);
    var secret = serde.deserialize(bytes, metadata);
    assertThat(secret.getType(), equalTo("opaque"));
    assertThat(secret.getEncoding(), equalTo("cbor"));
    assertThat(secret.getSecretString("bin"), equalTo("packed"));
  }
}
