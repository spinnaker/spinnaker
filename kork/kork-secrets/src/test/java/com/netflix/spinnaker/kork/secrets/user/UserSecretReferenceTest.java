package com.netflix.spinnaker.kork.secrets.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class UserSecretReferenceTest {

  @Test
  public void invalidSecretURI() {
    assertFalse(UserSecretReference.isUserSecret("secret"));
    assertFalse(UserSecretReference.isUserSecret("secretFile"));
    assertFalse(UserSecretReference.isUserSecret("file:///hello"));
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
}
