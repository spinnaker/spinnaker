package com.netflix.spinnaker.orca.front50.tasks;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.front50.multiplepipelines.App;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ParsePipelinesYamlTaskTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private PipelineExecutionImpl pipeline;

  private static final String testApplication = "test_app";
  private static final String testUser = "test_user";

  @BeforeEach
  public void setup() {
    pipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    pipeline.setAuthentication(new PipelineExecution.AuthenticationDetails(testUser));
  }

  @Test
  public void shouldParsePipelinesYamlTaskFinishSuccessfully() {
    var testPipelineId1 = "test_child_1";
    var testPipelineId2 = "test_child_2";

    var task = new ParsePipelinesYamlTask(objectMapper);

    var context = new HashMap<String, Object>();
    context.put(
        "yamlConfig",
        List.of(
            new HashMap() {
              {
                put(
                    "bundle_web",
                    new HashMap<>() {
                      {
                        put(
                            "test_child_1",
                            new HashMap<String, Object>() {
                              {
                                put("yamlIdentifier", testPipelineId1);
                                put("arguments", new HashMap<>());
                                put("child_pipeline", testPipelineId1);
                              }
                            });
                        put(
                            "test_child_2",
                            new HashMap<String, Object>() {
                              {
                                put("yamlIdentifier", testPipelineId2);
                                put("arguments", new HashMap<>());
                                put("child_pipeline", testPipelineId2);
                              }
                            });
                      }
                    });
              }
            }));

    var stageExecution = new StageExecutionImpl(pipeline, "runMultiplePipelines", context);

    var result = task.execute(stageExecution);

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());
    assertEquals(0, result.getContext().get("levelNumber"));

    var orderOfExecutions = (List<?>) result.getContext().get("orderOfExecutions");

    assertEquals(1, orderOfExecutions.size());

    var apps = (List<App>) orderOfExecutions.get(0);

    assertEquals(testPipelineId1, apps.get(0).getYamlIdentifier());
    assertEquals(testPipelineId1, apps.get(0).getChildPipeline());

    assertEquals(testPipelineId2, apps.get(1).getYamlIdentifier());
    assertEquals(testPipelineId2, apps.get(1).getChildPipeline());
  }
}
