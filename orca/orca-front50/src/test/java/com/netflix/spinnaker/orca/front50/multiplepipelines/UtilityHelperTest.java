package com.netflix.spinnaker.orca.front50.multiplepipelines;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class UtilityHelperTest {

  private final UtilityHelper utilityHelper = new UtilityHelper();

  @Test
  public void shouldCreateGraphOfAppsCorrectly() {
    var testAppsByYamlId = new HashMap<String, App>();

    var testApp1 = new App();
    testApp1.setChildPipeline("testChildPipeline1");
    testApp1.setDependsOn(new ArrayList<>());

    var testApp2 = new App();
    testApp2.setChildPipeline("testChildPipeline2");
    testApp2.setDependsOn(List.of("testApp1"));

    var testApp3 = new App();
    testApp3.setChildPipeline("testChildPipeline3");
    testApp3.setDependsOn(List.of("testApp2"));

    testAppsByYamlId.put("testApp1", testApp1);
    testAppsByYamlId.put("testApp2", testApp2);
    testAppsByYamlId.put("testApp3", testApp3);

    var testInitialExecutions = new ArrayList<App>();

    var graph = utilityHelper.getGraphOfApps(testAppsByYamlId, testInitialExecutions);

    var expectedGraph = GraphBuilder.directed().allowsSelfLoops(false).build();
    expectedGraph.putEdge(testApp1, testApp2);
    expectedGraph.putEdge(testApp2, testApp3);
    expectedGraph.addNode(testApp1);

    assertEquals(expectedGraph, graph);
    assertEquals(List.of(testApp1), testInitialExecutions);
  }

  @Test
  public void shouldSetOrderOfExecutionsCorrectly() {
    var testAppsByYamlId = new HashMap<String, App>();

    var testApp1 = new App();
    testApp1.setChildPipeline("testChildPipeline1");
    testApp1.setDependsOn(new ArrayList<>());

    var testApp2 = new App();
    testApp2.setChildPipeline("testChildPipeline2");
    testApp2.setDependsOn(List.of("testApp1"));

    var testApp3 = new App();
    testApp3.setChildPipeline("testChildPipeline3");
    testApp3.setDependsOn(List.of("testApp2"));

    var testApp4 = new App();
    testApp4.setChildPipeline("testChildPipeline4");
    testApp4.setDependsOn(new ArrayList<>());

    testAppsByYamlId.put("testApp1", testApp1);
    testAppsByYamlId.put("testApp2", testApp2);
    testAppsByYamlId.put("testApp3", testApp3);
    testAppsByYamlId.put("testApp4", testApp4);

    MutableGraph<App> graph = GraphBuilder.directed().allowsSelfLoops(false).build();
    graph.putEdge(testApp1, testApp2);
    graph.putEdge(testApp2, testApp3);
    graph.addNode(testApp1);
    graph.addNode(testApp4);

    List<List<App>> orderOfExecutions = new ArrayList<>();
    orderOfExecutions.add(
        new ArrayList<>() {
          {
            add(testApp1);
            add(testApp4);
          }
        });
    orderOfExecutions.add(
        new ArrayList<>() {
          {
            add(testApp2);
            add(testApp4);
          }
        });
    orderOfExecutions.add(
        new ArrayList<>() {
          {
            add(testApp3);
          }
        });

    utilityHelper.addLevels(orderOfExecutions, graph, new ArrayList<>(), 0);

    assertEquals(
        new ArrayList<>() {
          {
            add(
                new ArrayList<>() {
                  {
                    add(testApp1);
                    add(testApp4);
                  }
                });
            add(
                new ArrayList<>() {
                  {
                    add(testApp2);
                    add(testApp4);
                  }
                });
            add(
                new ArrayList<>() {
                  {
                    add(testApp3);
                  }
                });
            add(
                new ArrayList<>() {
                  {
                    add(testApp2);
                  }
                });
            add(
                new ArrayList<>() {
                  {
                    add(testApp3);
                  }
                });
          }
        },
        orderOfExecutions);
  }
}
