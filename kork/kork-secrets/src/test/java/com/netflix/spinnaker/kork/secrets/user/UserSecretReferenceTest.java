package com.netflix.spinnaker.kork.secrets.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class UserSecretReferenceTest {

  @Test
  public void invalidSecretURI() {
    assertFalse(UserSecretReference.isUserSecret("secret"));
    assertFalse(UserSecretReference.isUserSecret("secretFile"));
    assertFalse(UserSecretReference.isUserSecret("file:///hello"));
  }

  @Test
  public void missingQueryStringInSecretURI() {
    // ensure we're not throwing a NullPointerException
    assertThrows(
        InvalidUserSecretReferenceException.class, () -> UserSecretReference.parse("secret://foo"));
    assertFalse(UserSecretReference.tryParse("secret://foo").isPresent());
  }

  @Test
  public void queryParametersWithoutValuesAreInvalid() {
    // ensure we're not throwing a NullPointerException
    assertThrows(
        InvalidUserSecretReferenceException.class,
        () -> UserSecretReference.parse("secret://foo?bar"));
    assertFalse(UserSecretReference.tryParse("secret://foo?bar").isPresent());
  }

  @Test
  public void goodSecretURI() {
    var ref = UserSecretReference.parse("secret://foo?param1=bar1&param2=baz2");
    assertNotNull(ref);
    assertEquals("foo", ref.getEngineIdentifier());
    var parameters = ref.getParameters();
    assertEquals("bar1", parameters.get("param1"));
    assertEquals("baz2", parameters.get("param2"));
  }

  @Test
  public void encodedURIData() {
    var ref =
        UserSecretReference.parse(
            "secret://engine%20identifier?first=hello%20world&second=%68%65%6c%6c%6f%20%77%6f%72%6c%64");
    assertEquals("engine identifier", ref.getEngineIdentifier());
    var parameters = ref.getParameters();
    assertEquals("hello world", parameters.get("first"));
    assertEquals("hello world", parameters.get("second"));
  }

  @Test
  void queryParametersWithDuplicateKeysAreInvalid() {
    // instead of silently merging duplicate keys and using the last occurrence, this should throw
    // an exception
    assertThrows(
        InvalidUserSecretReferenceException.class,
        () -> UserSecretReference.parse("secret://engine?k=one&k=two&k=three"));
  }
}
