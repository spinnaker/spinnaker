/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipelinetemplate.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spinnaker.kork.jackson.Jackson2AccessorNamingStrategy;
import com.netflix.spinnaker.kork.yaml.YamlHelper;
import com.netflix.spinnaker.kork.yaml.YamlParserProperties;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class YamlObjectMapperFactoryTest {

  @Test
  void preservesJackson2AccessorNamingStrategyOnCreateWithoutYamlHelper() throws Exception {
    ObjectMapper source =
        JsonMapper.builder()
            .accessorNaming(new Jackson2AccessorNamingStrategy.Provider())
            .disable(MapperFeature.FIX_FIELD_NAME_UPPER_CASE_PREFIX)
            .build();

    ObjectMapper yamlMapper = YamlObjectMapperFactory.create(source);

    AcronymBean bean = new AcronymBean();
    bean.setOAuthScopes("scope");
    String yaml = yamlMapper.writeValueAsString(bean);
    assertTrue(
        yaml.contains("oauthScopes:"), "Expected yaml to contain 'oauthScopes:', got: " + yaml);
    assertFalse(
        yaml.contains("OAuthScopes:"), "Expected yaml not to contain 'OAuthScopes:', got: " + yaml);

    AcronymBean deserialized = yamlMapper.readValue("oauthScopes: legacy\n", AcronymBean.class);
    assertEquals("legacy", deserialized.getOAuthScopes());
  }

  @Test
  void preservesJackson2AccessorNamingStrategyOnCreateWithYamlHelper() throws Exception {
    ObjectMapper source =
        JsonMapper.builder()
            .accessorNaming(new Jackson2AccessorNamingStrategy.Provider())
            .disable(MapperFeature.FIX_FIELD_NAME_UPPER_CASE_PREFIX)
            .build();
    YamlHelper yamlHelper = new YamlHelper(new YamlParserProperties());

    ObjectMapper yamlMapper = YamlObjectMapperFactory.create(source, yamlHelper);

    AcronymBean bean = new AcronymBean();
    bean.setOAuthScopes("custom-scope");
    String yaml = yamlMapper.writeValueAsString(bean);
    assertTrue(
        yaml.contains("oauthScopes:"), "Expected yaml to contain 'oauthScopes:', got: " + yaml);
    assertFalse(
        yaml.contains("OAuthScopes:"), "Expected yaml not to contain 'OAuthScopes:', got: " + yaml);

    AcronymBean deserialized =
        yamlMapper.readValue("oauthScopes: helper-legacy\n", AcronymBean.class);
    assertEquals("helper-legacy", deserialized.getOAuthScopes());
  }

  @Test
  void preservesOrcaObjectMapperNamingStrategies() throws Exception {
    ObjectMapper source = OrcaObjectMapper.newInstance();
    ObjectMapper yamlMapper = YamlObjectMapperFactory.create(source);

    AcronymBean bean = new AcronymBean();
    bean.setOAuthScopes("orca-scope");
    String yaml = yamlMapper.writeValueAsString(bean);
    assertTrue(
        yaml.contains("oauthScopes:"), "Expected yaml to contain 'oauthScopes:', got: " + yaml);
    assertFalse(
        yaml.contains("OAuthScopes:"), "Expected yaml not to contain 'OAuthScopes:', got: " + yaml);

    AcronymBean deserialized =
        yamlMapper.readValue("oauthScopes: orca-legacy\n", AcronymBean.class);
    assertEquals("orca-legacy", deserialized.getOAuthScopes());
  }

  @Test
  void preservesExplicitPropertyNamingStrategyOnCreateWithoutYamlHelper() throws Exception {
    ObjectMapper source =
        JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();

    ObjectMapper yamlMapper = YamlObjectMapperFactory.create(source);

    ServiceBean bean = new ServiceBean();
    bean.setServiceAccountName("my-account");
    String yaml = yamlMapper.writeValueAsString(bean);
    assertTrue(
        yaml.contains("service_account_name:"),
        "Expected yaml to contain 'service_account_name:', got: " + yaml);

    ServiceBean deserialized =
        yamlMapper.readValue("service_account_name: updated-account\n", ServiceBean.class);
    assertEquals("updated-account", deserialized.getServiceAccountName());
  }

  @Test
  void preservesExplicitPropertyNamingStrategyOnCreateWithYamlHelper() throws Exception {
    ObjectMapper source =
        JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE).build();
    YamlHelper yamlHelper = new YamlHelper(new YamlParserProperties());

    ObjectMapper yamlMapper = YamlObjectMapperFactory.create(source, yamlHelper);

    ServiceBean bean = new ServiceBean();
    bean.setServiceAccountName("kebab-account");
    String yaml = yamlMapper.writeValueAsString(bean);
    assertTrue(
        yaml.contains("service-account-name:"),
        "Expected yaml to contain 'service-account-name:', got: " + yaml);

    ServiceBean deserialized =
        yamlMapper.readValue("service-account-name: kebab-updated\n", ServiceBean.class);
    assertEquals("kebab-updated", deserialized.getServiceAccountName());
  }

  static class AcronymBean {
    private String oAuthScopes = "scope";

    public String getOAuthScopes() {
      return oAuthScopes;
    }

    public void setOAuthScopes(String oAuthScopes) {
      this.oAuthScopes = oAuthScopes;
    }
  }

  static class ServiceBean {
    private String serviceAccountName = "test-account";

    public String getServiceAccountName() {
      return serviceAccountName;
    }

    public void setServiceAccountName(String serviceAccountName) {
      this.serviceAccountName = serviceAccountName;
    }
  }
}
