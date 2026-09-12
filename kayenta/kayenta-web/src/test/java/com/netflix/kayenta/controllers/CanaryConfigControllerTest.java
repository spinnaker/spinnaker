package com.netflix.kayenta.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.providers.metrics.AtlasCanaryMetricSetQueryConfig;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.io.InputStream;
import java.util.Collections;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

public class CanaryConfigControllerTest extends BaseControllerTest {

  private static final String CONFIG_ID = "canary_config_12345";

  @Autowired private CanaryConfigController canaryConfigController;

  @Test
  public void getCanaryConfig_returnsOkResponseForExistingConfiguration() throws Exception {
    CanaryConfig response = CanaryConfig.builder().application("test-app").build();
    when(storageService.loadObject(CONFIGS_ACCOUNT, ObjectType.CANARY_CONFIG, CONFIG_ID))
        .thenReturn(response);

    this.mockMvc
        .perform(
            get(
                "/canaryConfig/{configId}?configurationAccountName={account}",
                CONFIG_ID,
                CONFIGS_ACCOUNT))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.applications.length()").value(is(1)))
        .andExpect(jsonPath("$.applications[0]").value(is("test-app")));
  }

  @Test
  public void getCanaryConfig_returnsNotFoundResponseForNotExistingConfiguration()
      throws Exception {
    when(storageService.loadObject(CONFIGS_ACCOUNT, ObjectType.CANARY_CONFIG, CONFIG_ID))
        .thenThrow(new NotFoundException("dummy message"));

    this.mockMvc
        .perform(
            get(
                "/canaryConfig/{configId}?configurationAccountName={account}",
                CONFIG_ID,
                CONFIGS_ACCOUNT))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.message").value(is("dummy message")));
  }

  @Test
  public void getCanaryConfig_returnsBadRequestResponseForNotResolvedAccount() throws Exception {
    this.mockMvc
        .perform(
            get(
                "/canaryConfig/{configId}?configurationAccountName={account}",
                CONFIG_ID,
                "unknown-account"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.message", equalTo("Unable to resolve account unknown-account.")));
  }

  @Test
  public void postCanaryConfig_returnsBadRequestResponseForDuplicateMetricName() throws Exception {
    InputStream testConfig =
        BaseControllerTest.class
            .getClassLoader()
            .getResourceAsStream("canary-configs/duplicated-metric-name.json");
    String testConfigContent = IOUtils.toString(testConfig, "UTF-8");

    this.mockMvc
        .perform(post("/canaryConfig").contentType("application/json").content(testConfigContent))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.message", endsWith("'mem' is duplicated.")));
  }

  @Test
  public void getCanaryConfig_normalizesLegacyCustomFilterTemplateIntoTemplate() throws Exception {
    AtlasCanaryMetricSetQueryConfig legacyQuery =
        AtlasCanaryMetricSetQueryConfig.builder().customFilterTemplate("named-template").build();
    CanaryMetricConfig legacyMetric =
        CanaryMetricConfig.builder().name("requests").query(legacyQuery).build();
    CanaryConfig storedConfig =
        CanaryConfig.builder()
            .application("test-app")
            .metric(legacyMetric)
            .templates(Collections.singletonMap("named-template", "name,requestsPerSecond,:eq"))
            .build();

    when(storageService.loadObject(CONFIGS_ACCOUNT, ObjectType.CANARY_CONFIG, CONFIG_ID))
        .thenReturn(storedConfig);

    this.mockMvc
        .perform(
            get(
                "/canaryConfig/{configId}?configurationAccountName={account}",
                CONFIG_ID,
                CONFIGS_ACCOUNT))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.metrics[0].query.template").value(is("name,requestsPerSecond,:eq")))
        .andExpect(jsonPath("$.metrics[0].query.customFilterTemplate").doesNotExist());
  }

  // These two tests exercise storeCanaryConfig() by calling the controller bean directly rather
  // than posting JSON through MockMvc: this test module's narrow @SpringBootTest slice (see
  // BaseControllerTest) doesn't wire up KayentaConfiguration's ObjectMapperSubtypeConfigurer, so
  // MockMvc's own request-body ObjectMapper doesn't know how to resolve the polymorphic
  // CanaryMetricSetQueryConfig "type" discriminator (e.g. "atlas") and would 400 on any config
  // with a metric query in it. Deserializing with a locally, correctly-configured ObjectMapper
  // (mirroring how KayentaConfiguration wires the real one) and passing the resulting CanaryConfig
  // straight into the real controller bean still exercises genuine JSON deserialization -- proving
  // @JsonAlias and normalizeMetricTemplates end-to-end -- without depending on that unrelated test
  // infra gap.
  private static CanaryConfig deserializeLegacyCanaryConfig(String json) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerSubtypes(AtlasCanaryMetricSetQueryConfig.class);
    return objectMapper.readValue(json, CanaryConfig.class);
  }

  @Test
  public void postCanaryConfig_persistsLegacyCustomFilterTemplateAsNormalizedTemplate()
      throws Exception {
    when(storageService.loadObject(eq(CONFIGS_ACCOUNT), eq(ObjectType.CANARY_CONFIG), anyString()))
        .thenThrow(new NotFoundException("not found"));

    String testConfigContent =
        "{"
            + "\"name\":\"legacy-config\","
            + "\"applications\":[\"some-application\"],"
            + "\"metrics\":[{"
            + "\"name\":\"requests\","
            + "\"groups\":[\"System\"],"
            + "\"analysisConfigurations\":{},"
            + "\"scopeName\":\"default\","
            + "\"query\":{\"type\":\"atlas\",\"customFilterTemplate\":\"named-template\"}"
            + "}],"
            + "\"templates\":{\"named-template\":\"name,requestsPerSecond,:eq\"}"
            + "}";

    CanaryConfig canaryConfig = deserializeLegacyCanaryConfig(testConfigContent);

    canaryConfigController.storeCanaryConfig(CONFIGS_ACCOUNT, canaryConfig);

    ArgumentCaptor<CanaryConfig> captor = ArgumentCaptor.forClass(CanaryConfig.class);
    verify(storageService)
        .storeObject(
            eq(CONFIGS_ACCOUNT),
            eq(ObjectType.CANARY_CONFIG),
            anyString(),
            captor.capture(),
            anyString(),
            anyBoolean());

    AtlasCanaryMetricSetQueryConfig persistedQuery =
        (AtlasCanaryMetricSetQueryConfig) captor.getValue().getMetrics().get(0).getQuery();

    assertThat(persistedQuery.getTemplate()).isEqualTo("name,requestsPerSecond,:eq");
    assertThat(persistedQuery.getCustomFilterTemplate()).isNull();
  }

  @Test
  public void postCanaryConfig_legacyCustomInlineTemplateJsonKeyPopulatesTemplateField()
      throws Exception {
    when(storageService.loadObject(eq(CONFIGS_ACCOUNT), eq(ObjectType.CANARY_CONFIG), anyString()))
        .thenThrow(new NotFoundException("not found"));

    String testConfigContent =
        "{"
            + "\"name\":\"legacy-inline-config\","
            + "\"applications\":[\"some-application\"],"
            + "\"metrics\":[{"
            + "\"name\":\"requests\","
            + "\"groups\":[\"System\"],"
            + "\"analysisConfigurations\":{},"
            + "\"scopeName\":\"default\","
            + "\"query\":{\"type\":\"atlas\",\"customInlineTemplate\":\"name,requestsPerSecond,:eq\"}"
            + "}]"
            + "}";

    CanaryConfig canaryConfig = deserializeLegacyCanaryConfig(testConfigContent);

    // Prove @JsonAlias worked before it even reaches the controller.
    AtlasCanaryMetricSetQueryConfig deserializedQuery =
        (AtlasCanaryMetricSetQueryConfig) canaryConfig.getMetrics().get(0).getQuery();
    assertThat(deserializedQuery.getTemplate()).isEqualTo("name,requestsPerSecond,:eq");

    canaryConfigController.storeCanaryConfig(CONFIGS_ACCOUNT, canaryConfig);

    ArgumentCaptor<CanaryConfig> captor = ArgumentCaptor.forClass(CanaryConfig.class);
    verify(storageService)
        .storeObject(
            eq(CONFIGS_ACCOUNT),
            eq(ObjectType.CANARY_CONFIG),
            anyString(),
            captor.capture(),
            anyString(),
            anyBoolean());

    AtlasCanaryMetricSetQueryConfig persistedQuery =
        (AtlasCanaryMetricSetQueryConfig) captor.getValue().getMetrics().get(0).getQuery();

    assertThat(persistedQuery.getTemplate()).isEqualTo("name,requestsPerSecond,:eq");
  }
}
