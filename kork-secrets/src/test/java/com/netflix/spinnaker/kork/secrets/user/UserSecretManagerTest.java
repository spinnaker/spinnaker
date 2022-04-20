package com.netflix.spinnaker.kork.secrets.user;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.netflix.spinnaker.kork.secrets.SecretConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@SpringBootTest(classes = SecretConfiguration.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class UserSecretManagerTest {

  @Autowired UserSecretManager userSecretManager;

  @Test
  public void getTestSecret() {
    var ref = UserSecretReference.parse("secret://noop?v=test");
    var secret = userSecretManager.getUserSecret(ref);
    assertEquals("test", secret.getSecretString("v"));
    assertTrue(secret instanceof OpaqueUserSecret);
    assertTrue(((OpaqueUserSecret) secret).getRoles().isEmpty());
  }

  @Test
  public void getTestSecretString() {
    var ref = UserSecretReference.parse("secret://noop?foo=bar");
    var userSecret = userSecretManager.getUserSecret(ref);
    assertEquals("bar", userSecret.getSecretString("foo"));
  }
}
