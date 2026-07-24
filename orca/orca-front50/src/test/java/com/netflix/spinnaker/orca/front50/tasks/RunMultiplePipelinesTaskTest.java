package com.netflix.spinnaker.orca.front50.tasks;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.front50.DependentPipelineStarter;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
public class RunMultiplePipelinesTaskTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private PipelineExecutionImpl pipeline;

  private Front50Service front50Service;
  private DependentPipelineStarter dependentPipelineStarter;

  private static final String testApplication = "test_app";
  private static final String testUser = "test_user";

  @BeforeEach
  public void setup() {
    pipeline = new PipelineExecutionImpl(PIPELINE, testApplication);
    pipeline.setAuthentication(new PipelineExecution.AuthenticationDetails(testUser));

    front50Service = mock(Front50Service.class);
    dependentPipelineStarter = mock(DependentPipelineStarter.class);
  }

  @Test
  public void shouldMultiplePipelinesTaskFinishSuccessfully() {
    var testPipelineId1 = "test_child_1";
    var testPipelineId2 = "test_child_2";

    var task =
        new RunMultiplePipelinesTask(
            Optional.of(front50Service), dependentPipelineStarter, objectMapper);

    var context = new HashMap<String, Object>();
    context.put("levelNumber", 0);
    context.put(
        "orderOfExecutions",
        List.of(
            List.of(
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId1);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId1);
                  }
                },
                new HashMap<String, Object>() {
                  {
                    put("yamlIdentifier", testPipelineId2);
                    put("arguments", new HashMap<>());
                    put("child_pipeline", testPipelineId2);
                  }
                })));

    var stageExecution = new StageExecutionImpl(pipeline, "runMultiplePipelines", context);

    pipeline.getStages().add(stageExecution);

    when(front50Service.getPipelines(testApplication, false))
        .thenReturn(
            Calls.response(
                List.<Map<String, Object>>of(
                    new HashMap<>() {
                      {
                        put("id", "test_parent");
                        put("name", "test_parent");
                        put("application", testApplication);
                        put("index", 0);
                      }
                    },
                    new HashMap<>() {
                      {
                        put("id", testPipelineId1);
                        put("name", testPipelineId1);
                        put("application", testApplication);
                        put("index", 1);
                      }
                    },
                    new HashMap<>() {
                      {
                        put("id", testPipelineId2);
                        put("name", testPipelineId2);
                        put("application", testApplication);
                        put("index", 2);
                      }
                    })));

    var childPipeline1 = new PipelineExecutionImpl(PIPELINE, testApplication);
    var childPipeline2 = new PipelineExecutionImpl(PIPELINE, testApplication);

    when(dependentPipelineStarter.trigger(any(), eq(testUser), any(), any(), any(), any()))
        .thenReturn(childPipeline1, childPipeline2);

    var result = task.execute(stageExecution);

    assertEquals(ExecutionStatus.SUCCEEDED, result.getStatus());

    var executionIds = (List<String>) result.getContext().get("executionIds");

    assertTrue(executionIds.contains(childPipeline1.getId()));
    assertTrue(executionIds.contains(childPipeline2.getId()));
  }
}
