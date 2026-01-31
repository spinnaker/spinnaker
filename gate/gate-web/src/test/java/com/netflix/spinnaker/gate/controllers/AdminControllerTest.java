/*
 * Copyright 2026 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.controllers;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import com.netflix.spinnaker.gate.services.internal.OrcaService;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import com.netflix.spinnaker.kork.common.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import retrofit2.mock.Calls;

@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "header.enabled=true",
      "services.fiat.enabled=true",
      "fiat.session-filter.enabled=false"
    })
class AdminControllerTest {

  private static final String TEST_USER = "test-user";

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext webApplicationContext;

  @MockBean private OrcaServiceSelector orcaServiceSelector;
  @MockBean private OrcaService orcaService;
  @MockBean private FiatPermissionEvaluator fiatPermissionEvaluator;
  @MockBean private FiatStatus fiatStatus;

  /** Mock the application service to disable the background thread that caches applications */
  @MockBean private ApplicationService applicationService;

  /** To prevent periodic calls to service's /health endpoints */
  @MockBean private DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  /** To prevent periodic calls to clouddriver to query for accounts */
  @MockBean private DefaultProviderLookupService defaultProviderLookupService;

  @BeforeEach
  void setUp() {
    mockMvc = webAppContextSetup(webApplicationContext).build();
    when(fiatStatus.isEnabled()).thenReturn(true);
  }

  @Test
  @WithMockUser(username = TEST_USER)
  @DisplayName("Should force cancel a pipeline execution when user is admin")
  void forceCancelPipelineExecutionAsAdmin() throws Exception {
    String executionId = "test-execution-id";
    String executionType = "PIPELINE";

    when(fiatPermissionEvaluator.isAdmin()).thenReturn(true);
    when(orcaServiceSelector.select()).thenReturn(orcaService);
    when(orcaService.forceCancelExecution(executionId, executionType, TEST_USER))
        .thenReturn(Calls.response((Void) null));

    mockMvc
        .perform(
            put("/admin/executions/forceCancel")
                .header(Header.USER.getHeader(), TEST_USER)
                .queryParam("executionId", executionId)
                .queryParam("executionType", executionType)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    verify(orcaService).forceCancelExecution(executionId, executionType, TEST_USER);
  }

  @Test
  @WithMockUser(username = TEST_USER)
  @DisplayName("Should return bad request when executionId is missing")
  void forceCancelExecutionMissingExecutionId() throws Exception {
    when(fiatPermissionEvaluator.isAdmin()).thenReturn(true);

    mockMvc
        .perform(
            put("/admin/executions/forceCancel")
                .header(Header.USER.getHeader(), TEST_USER)
                .queryParam("executionType", "PIPELINE")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(orcaServiceSelector);
    verifyNoInteractions(orcaService);
  }

  @Test
  @WithMockUser(username = TEST_USER)
  @DisplayName("Should return bad request when executionType is missing")
  void forceCancelExecutionMissingExecutionType() throws Exception {
    when(fiatPermissionEvaluator.isAdmin()).thenReturn(true);

    mockMvc
        .perform(
            put("/admin/executions/forceCancel")
                .header(Header.USER.getHeader(), TEST_USER)
                .queryParam("executionId", "test-execution-id")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(orcaServiceSelector);
    verifyNoInteractions(orcaService);
  }

  @Test
  @WithMockUser(username = TEST_USER)
  @DisplayName("Should return 403 when user is not admin")
  void forceCancelExecutionAccessDenied() throws Exception {
    String executionId = "test-execution-id";
    String executionType = "PIPELINE";

    when(fiatPermissionEvaluator.isAdmin()).thenReturn(false);

    mockMvc
        .perform(
            put("/admin/executions/forceCancel")
                .header(Header.USER.getHeader(), TEST_USER)
                .queryParam("executionId", executionId)
                .queryParam("executionType", executionType)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());

    // Verify that @PreAuthorize actually called the fiatPermissionEvaluator
    verify(fiatPermissionEvaluator).isAdmin();

    verifyNoInteractions(orcaServiceSelector);
    verifyNoInteractions(orcaService);
  }
}
