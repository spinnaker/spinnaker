package com.netflix.spinnaker.orca.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.Front50Service.Project;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import io.reactivex.rxjava3.core.Observable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {
  @Mock private ExecutionRepository executionRepository;

  @Mock private Front50Service front50Service;

  @InjectMocks private ProjectController controller;

  @Mock private Call<Project> mockCall;

  @BeforeEach
  void setup() {
    reset(executionRepository, front50Service, mockCall);
  }

  @Test
  void shouldHandleProjectListResponse_emtpyPipelineConfigs() throws Exception {
    // Create a project response with empty PipelineConfigIds
    Project malformedProject = new Project();
    malformedProject.setId("test-project");
    malformedProject.setName("Test Project");
    malformedProject.setConfig(new Project.ProjectConfig());

    when(front50Service.getProject("test-project")).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(malformedProject));

    List<PipelineExecution> result = controller.list("test-project", 5, null);
    assertEquals(0, result.size());
    assertEquals(List.of(), result);
  }

  @Test
  void shouldRetrievePipelinesForValidProject() throws Exception {
    //  Create a project response with PipelineConfigIds
    Project project = new Project();
    project.setId("test-project");
    project.setName("Test Project");

    Project.ProjectConfig config = new Project.ProjectConfig();
    List<Project.PipelineConfig> pipelineConfigs = new ArrayList<>();

    Project.PipelineConfig pipeline1 = new Project.PipelineConfig();
    pipeline1.setPipelineConfigId("pipeline-1");
    pipelineConfigs.add(pipeline1);

    Project.PipelineConfig pipeline2 = new Project.PipelineConfig();
    pipeline2.setPipelineConfigId("pipeline-2");
    pipelineConfigs.add(pipeline2);

    config.setPipelineConfigs(pipelineConfigs);
    project.setConfig(config);

    // Mock Front50Service
    when(front50Service.getProject("test-project")).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(project));

    // Mock ExecutionRepository
    PipelineExecution execution1 = mock(PipelineExecution.class);
    when(execution1.getStartTime()).thenReturn(1000L);
    when(execution1.getId()).thenReturn("exec-1");

    PipelineExecution execution2 = mock(PipelineExecution.class);
    when(execution2.getStartTime()).thenReturn(2000L);
    when(execution2.getId()).thenReturn("exec-2");

    // Set up repository to return executions
    when(executionRepository.retrievePipelinesForPipelineConfigId(eq("pipeline-1"), any()))
        .thenReturn(Observable.fromIterable(Collections.singletonList(execution1)));
    when(executionRepository.retrievePipelinesForPipelineConfigId(eq("pipeline-2"), any()))
        .thenReturn(Observable.fromIterable(Collections.singletonList(execution2)));

    List<PipelineExecution> result = controller.list("test-project", 5, null);

    assertNotNull(result);
    assertEquals(2, result.size());
    // Executions should be sorted by startTime in descending order
    assertEquals("exec-1", result.get(0).getId());
    assertEquals("exec-2", result.get(1).getId());

    // Verify that the repository was called with the right parameters
    verify(executionRepository).retrievePipelinesForPipelineConfigId(eq("pipeline-1"), any());
    verify(executionRepository).retrievePipelinesForPipelineConfigId(eq("pipeline-2"), any());
  }
}
