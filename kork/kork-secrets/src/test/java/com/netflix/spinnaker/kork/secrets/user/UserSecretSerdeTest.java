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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import com.netflix.spinnaker.kork.secrets.SecretConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
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
    assertThat(
        secret.getSecretString(UserSecretReference.parse("secret://mock?k=foo")), equalTo("bar"));
    assertThat(
        secret.getSecretString(UserSecretReference.parse("secret://mock?k=second")),
        equalTo("second"));
  }

  @Test
  public void invalidSecretJson() {
    var metadata =
        UserSecretMetadata.builder().type("opaque").encoding("json").roles(List.of()).build();
    var serde = factory.serdeFor(metadata);
    var bytes =
        serde.serialize(
            new OpaqueUserSecretData(Map.of("foo", "bar", "second", "second")), metadata);
    bytes[0] = '?'; // corrupt the data
    try {
      serde.deserialize(bytes, metadata);
    } catch (InvalidUserSecretDataException e) { // look for leaked data in the error message
      assertThat(
          e.getMessage(),
          equalTo(
              "the secret value does not seem to be valid JSON: unknown error encountered while decoding the contents as JSON"));

      // print entire stack trace into a string
      var stackTrace = new java.io.StringWriter();
      e.printStackTrace(new java.io.PrintWriter(stackTrace));

      // collect representations of the exception, strings that we might log
      List<String> exceptionPrintouts = List.of(stackTrace.toString(), e.getMessage());

      // check that none of the exception representations contain the secret data
      List<String> unexpectedStrings = List.of("foo", "bar", "second");
      exceptionPrintouts.forEach(
          printout ->
              unexpectedStrings.forEach(
                  secretWord -> assertThat(printout, not(containsString(secretWord)))));
    } catch (Exception e) {
      fail("Unexpected exception class: " + e.getClass().getName());
    }
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
    assertThat(
        secret.getSecretString(UserSecretReference.parse("secret://mock?k=a")), equalTo("A"));
    assertThat(
        secret.getSecretString(UserSecretReference.parse("secret://mock?k=b")), equalTo("B"));
    assertThat(
        secret.getSecretString(UserSecretReference.parse("secret://mock?k=c")), equalTo("C"));
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
    assertThat(
        secret.getSecretString(UserSecretReference.parse("secret://mock?k=bin")),
        equalTo("packed"));
  }

  @Test
  void nullEncodingDoesNotThrowNullPointerException() {
    var metadata = UserSecretMetadata.builder().type("opaque").build();
    assertThrows(UnsupportedUserSecretTypeException.class, () -> factory.serdeFor(metadata));
  }
}
