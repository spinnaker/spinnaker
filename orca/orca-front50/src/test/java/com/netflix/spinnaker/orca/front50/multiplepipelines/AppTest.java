package com.netflix.spinnaker.orca.front50.multiplepipelines;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AppTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void shouldDeserializeCorrectly() throws JsonProcessingException {
    var testAppJson =
        "{"
            + "\"arguments\": {"
            + "\"foo\": 101,"
            + "\"bar\": 102"
            + "},"
            + "\"child_pipeline\": \"test_child_pipeline_a\","
            + "\"depends_on\": [\"test_child_pipeline_b\"],"
            + "\"yamlIdentifier\": \"test_yaml_identifier\""
            + "}";
    var app = objectMapper.readValue(testAppJson, App.class);

    assertEquals(
        new HashMap<>() {
          {
            put("foo", 101);
            put("bar", 102);
          }
        },
        app.getArguments());

    assertEquals("test_child_pipeline_a", app.getChildPipeline());
    assertEquals(List.of("test_child_pipeline_b"), app.getDependsOn());
    assertEquals("test_yaml_identifier", app.getYamlIdentifier());
  }

  @Test
  public void shouldBeEqual() {
    var arguments =
        new HashMap<String, Object>() {
          {
            put("foo", 101);
            put("bar", 102);
          }
        };
    var childPipeline = "test_child_pipeline_a";

    var testApp1 = new App();
    testApp1.setArguments(arguments);
    testApp1.setChildPipeline(childPipeline);
    testApp1.setDependsOn(List.of("test_child_pipeline_b"));
    testApp1.setYamlIdentifier("a");

    var testApp2 = new App();
    testApp2.setArguments(arguments);
    testApp2.setChildPipeline(childPipeline);
    testApp2.setDependsOn(List.of("test_child_pipeline_c"));
    testApp2.setYamlIdentifier("b");

    assertEquals(testApp1, testApp2);
    assertEquals(testApp2, testApp1);
  }

  @Test
  public void shouldNotBeEqual() {
    var testYamlIdentifier = "a";
    var testDependsOn = List.of("test_child_pipeline_a");

    var testApp1 = new App();
    testApp1.setArguments(
        new HashMap<>() {
          {
            put("foo", 101);
          }
        });
    testApp1.setChildPipeline("test_child_pipeline_b");
    testApp1.setDependsOn(testDependsOn);
    testApp1.setYamlIdentifier(testYamlIdentifier);

    var testApp2 = new App();
    testApp2.setArguments(
        new HashMap<>() {
          {
            put("bar", 102);
          }
        });
    testApp2.setChildPipeline("test_child_pipeline_c");
    testApp2.setDependsOn(testDependsOn);
    testApp2.setYamlIdentifier(testYamlIdentifier);

    assertNotEquals(testApp1, testApp2);
    assertNotEquals(testApp2, testApp1);
  }

  @Test
  public void shouldNotBeEqualNull() {
    var testApp = new App();
    testApp.setArguments(
        new HashMap<>() {
          {
            put("foo", 101);
          }
        });
    testApp.setChildPipeline("test_child_pipeline_a");
    testApp.setDependsOn(List.of("test_child_pipeline_b"));
    testApp.setYamlIdentifier("a");

    assertNotEquals(null, testApp);
  }

  @Test
  public void shouldStringifyCorrectly() {
    var testApp = new App();
    testApp.setArguments(
        new HashMap<>() {
          {
            put("foo", 101);
          }
        });
    testApp.setChildPipeline("test_child_pipeline_a");
    testApp.setDependsOn(List.of("test_child_pipeline_b"));
    testApp.setYamlIdentifier("a");

    assertEquals(
        "App("
            + "arguments={foo=101}, "
            + "childPipeline=test_child_pipeline_a, "
            + "dependsOn=[test_child_pipeline_b], "
            + "yamlIdentifier=a)",
        testApp.toString());
  }

  @Test
  public void shouldHashCodeBeCorrect() {
    var testApp = new App();

    assertEquals(3524, testApp.hashCode());

    testApp.setArguments(
        new HashMap<>() {
          {
            put("foo", 101);
          }
        });
    testApp.setChildPipeline("test_child_pipeline_a");
    testApp.setDependsOn(List.of("test_child_pipeline_b"));
    testApp.setYamlIdentifier("a");

    assertEquals(2131884094, testApp.hashCode());
  }
}
