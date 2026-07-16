package com.netflix.spinnaker.orca.front50.multiplepipelines;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AppsTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void shouldDeserializeCorrectly() throws JsonProcessingException {
    var testAppsJson =
        "{"
            + "\"apps\": {"
            + "\"foo\": 101,"
            + "\"bar\": 102"
            + "},"
            + "\"rollback_onfailure\": true,"
            + "\"baz\": 103"
            + "}";
    var apps = objectMapper.readValue(testAppsJson, Apps.class);

    assertEquals(
        new HashMap<>() {
          {
            put("foo", 101);
            put("bar", 102);
            put("baz", 103);
          }
        },
        apps.getApps());

    assertTrue(apps.isRollbackOnFailure());
  }

  @Test
  public void shouldBeEqual() {
    var testApps =
        new HashMap<String, Object>() {
          {
            put("foo", 101);
            put("bar", 102);
          }
        };

    var testRollbackOnFailure = true;

    var testApps1 = new Apps();
    testApps1.setApps(testApps);
    testApps1.setRollbackOnFailure(testRollbackOnFailure);

    var testApps2 = new Apps();
    testApps2.setApps(testApps);
    testApps2.setRollbackOnFailure(testRollbackOnFailure);

    assertEquals(testApps1, testApps2);
    assertEquals(testApps2, testApps1);
  }

  @Test
  public void shouldNotBeEqual() {
    var testRollbackOnFailure = true;

    var testApps1 = new Apps();
    testApps1.setApps(
        new HashMap<>() {
          {
            put("foo", 101);
          }
        });
    testApps1.setRollbackOnFailure(testRollbackOnFailure);

    var testApps2 = new Apps();
    testApps2.setApps(
        new HashMap<>() {
          {
            put("bar", 102);
          }
        });
    testApps2.setRollbackOnFailure(testRollbackOnFailure);

    assertNotEquals(testApps1, testApps2);
    assertNotEquals(testApps2, testApps1);

    var testApps =
        new HashMap<String, Object>() {
          {
            put("foo", 101);
            put("bar", 102);
          }
        };

    testApps1 = new Apps();
    testApps1.setApps(testApps);
    testApps1.setRollbackOnFailure(true);

    testApps2 = new Apps();
    testApps2.setApps(testApps);
    testApps2.setRollbackOnFailure(false);

    assertNotEquals(testApps1, testApps2);
    assertNotEquals(testApps2, testApps1);
  }

  @Test
  public void shouldNotBeEqualNull() {
    var testApps = new Apps();
    testApps.setApps(
        new HashMap<>() {
          {
            put("foo", 101);
            put("bar", 102);
          }
        });
    testApps.setRollbackOnFailure(true);

    assertNotEquals(null, testApps);
  }

  @Test
  public void shouldStringifyCorrectly() {
    var testApps = new Apps();
    testApps.setApps(
        new HashMap<>() {
          {
            put("foo", 101);
            put("bar", 102);
          }
        });
    testApps.setRollbackOnFailure(true);

    assertEquals("Apps(apps={bar=102, foo=101}, rollbackOnFailure=true)", testApps.toString());
  }

  @Test
  public void shouldHashCodeBeCorrect() {
    var testApps = new Apps();

    assertEquals(9204, testApps.hashCode());
    testApps.setApps(
        new HashMap<>() {
          {
            put("foo", 101);
            put("bar", 102);
          }
        });
    testApps.setRollbackOnFailure(true);

    assertEquals(207078, testApps.hashCode());
  }
}
