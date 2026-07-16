package com.netflix.spinnaker.orca.front50.multiplepipelines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BundleWebTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void shouldDeserializeCorrectly() throws JsonProcessingException {
    var testBundleWebJson =
        "{" + "\"bundle_web\": {" + "\"foo\": 101," + "\"bar\": 102" + "}" + "}";
    var bundleWeb = objectMapper.readValue(testBundleWebJson, BundleWeb.class);

    assertEquals(
        new HashMap<>() {
          {
            put("foo", 101);
            put("bar", 102);
          }
        },
        bundleWeb.getBundleWeb());
  }

  @Test
  public void shouldBeEqual() {
    var bundleWeb =
        new HashMap<String, Object>() {
          {
            put("foo", 101);
            put("bar", 102);
          }
        };

    var testBundleWeb1 = new BundleWeb();
    testBundleWeb1.setBundleWeb(bundleWeb);

    var testBundleWeb2 = new BundleWeb();
    testBundleWeb2.setBundleWeb(bundleWeb);

    assertEquals(testBundleWeb1, testBundleWeb2);
    assertEquals(testBundleWeb2, testBundleWeb1);
  }

  @Test
  public void shouldNotBeEqual() {
    var testBundleWeb1 = new BundleWeb();
    testBundleWeb1.setBundleWeb(
        new HashMap<>() {
          {
            put("foo", 101);
          }
        });

    var testBundleWeb2 = new BundleWeb();
    testBundleWeb2.setBundleWeb(
        new HashMap<>() {
          {
            put("bar", 102);
          }
        });

    assertNotEquals(testBundleWeb1, testBundleWeb2);
    assertNotEquals(testBundleWeb2, testBundleWeb1);
  }

  @Test
  public void shouldNotBeEqualNull() {
    var testBundleWeb = new BundleWeb();
    testBundleWeb.setBundleWeb(
        new HashMap<>() {
          {
            put("foo", 101);
            put("bar", 102);
          }
        });

    assertNotEquals(null, testBundleWeb);
  }

  @Test
  public void shouldStringifyCorrectly() {
    var testBundleWeb = new BundleWeb();
    testBundleWeb.setBundleWeb(
        new HashMap<>() {
          {
            put("foo", 101);
            put("bar", 102);
          }
        });

    assertEquals("BundleWeb(bundleWeb={bar=102, foo=101})", testBundleWeb.toString());
  }

  @Test
  public void shouldHashCodeBeCorrect() {
    var testBundleWeb = new BundleWeb();

    assertEquals(102, testBundleWeb.hashCode());
    testBundleWeb.setBundleWeb(
        new HashMap<>() {
          {
            put("foo", 101);
            put("bar", 102);
          }
        });

    assertEquals(198995, testBundleWeb.hashCode());
  }
}
