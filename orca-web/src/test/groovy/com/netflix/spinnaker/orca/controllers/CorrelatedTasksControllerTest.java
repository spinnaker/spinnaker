package com.netflix.spinnaker.orca.controllers;

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION;
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.test.model.ExecutionBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {CorrelatedTasksController.class},
    webEnvironment = MOCK)
@AutoConfigureMockMvc
@EnableWebMvc
@WithMockUser("Bea O'Problem")
class CorrelatedTasksControllerTest {

  @MockBean ExecutionRepository executionRepository;

  @Autowired MockMvc mvc;

  final String correlationId = randomUUID().toString();
  final MockHttpServletRequestBuilder request =
      get("/executions/correlated/{correlationId}", correlationId);

  @Test
  void returnsEmptyResponseIfNoCorrelatedExecutionsExist() throws Exception {
    when(executionRepository.retrieveByCorrelationId(any(), eq(correlationId)))
        .thenThrow(ExecutionNotFoundException.class);

    mvc.perform(request).andExpect(status().is2xxSuccessful()).andExpect(content().json("[]"));
  }

  @Test
  void returnsIdsOfAnyCorrelatedPipelines() throws Exception {
    Execution pipeline = ExecutionBuilder.pipeline();
    when(executionRepository.retrieveByCorrelationId(PIPELINE, correlationId)).thenReturn(pipeline);
    when(executionRepository.retrieveByCorrelationId(ORCHESTRATION, correlationId))
        .thenThrow(ExecutionNotFoundException.class);

    mvc.perform(request)
        .andExpect(status().is2xxSuccessful())
        .andExpect(content().json(format("[\"%s\"]", pipeline.getId())));
  }

  @Test
  void returnsIdsOfAnyCorrelatedOrchestrations() throws Exception {
    Execution orchestration = ExecutionBuilder.orchestration();
    when(executionRepository.retrieveByCorrelationId(PIPELINE, correlationId))
        .thenThrow(ExecutionNotFoundException.class);
    when(executionRepository.retrieveByCorrelationId(ORCHESTRATION, correlationId))
        .thenReturn(orchestration);

    mvc.perform(request)
        .andExpect(status().is2xxSuccessful())
        .andExpect(content().json(format("[\"%s\"]", orchestration.getId())));
  }
}
