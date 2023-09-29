package com.netflix.kayenta.controllers;

import static org.hamcrest.core.StringContains.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.storage.ObjectType;
import org.junit.jupiter.api.Test;

public class CanaryControllerTest extends BaseControllerTest {

  private static final String CONFIG_ID = "canary_config_12345";

  @Test
  public void initiateCanary_failsIfNoMetricsSpecified() throws Exception {
    CanaryConfig response = CanaryConfig.builder().application("test-app").build();
    when(storageService.loadObject(CONFIGS_ACCOUNT, ObjectType.CANARY_CONFIG, CONFIG_ID))
        .thenReturn(response);

    this.mockMvc
        .perform(
            post("/canary/{configId}?application=test-app", CONFIG_ID)
                .contentType("application/json")
                .content(
                    "{\"scopes\":{\"default\":{\"controlScope\":{\"scope\":\"testapp-baseline\",\"location\":\"us-east-1\",\"start\":\"2020-07-27T19:17:36Z\",\"end\":\"2020-07-27T19:21:36Z\",\"step\":60,\"extendedScopeParams\":{\"type\":\"cluster\",\"environment\":\"prod\",\"dataset\":\"regional\",\"deployment\":\"main\"}},\"experimentScope\":{\"scope\":\"testapp-canary\",\"location\":\"us-east-1\",\"start\":\"2020-07-27T19:17:36Z\",\"end\":\"2020-07-27T19:21:36Z\",\"step\":60,\"extendedScopeParams\":{\"type\":\"cluster\",\"environment\":\"prod\",\"dataset\":\"regional\",\"deployment\":\"main\"}}}},\"thresholds\":{\"pass\":95.0,\"marginal\":75.0}}"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.message").value(containsString("at least one metric")));
  }
}
