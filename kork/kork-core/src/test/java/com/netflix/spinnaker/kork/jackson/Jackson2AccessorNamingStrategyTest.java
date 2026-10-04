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
package com.netflix.spinnaker.kork.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonPOJOBuilder;
import tools.jackson.databind.json.JsonMapper;

class Jackson2AccessorNamingStrategyTest {
  private final JsonMapper mapper =
      JsonMapper.builder()
          .accessorNaming(new Jackson2AccessorNamingStrategy.Provider())
          .disable(MapperFeature.FIX_FIELD_NAME_UPPER_CASE_PREFIX)
          .build();

  @Test
  void acronymAccessorsUseLegacyNamesForSerializationAndDeserialization() {
    AcronymBean bean = new AcronymBean();
    bean.setOAuthScopes("scope");
    bean.setURL("url");
    bean.setHTTPS(true);
    String json = mapper.writeValueAsString(bean);
    assertEquals("scope", mapper.readTree(json).path("oauthScopes").asString());
    assertEquals("url", mapper.readTree(json).path("url").asString());
    assertEquals(true, mapper.readTree(json).path("https").asBoolean());
    AcronymBean read = mapper.readValue(json, AcronymBean.class);
    assertEquals("scope", read.getOAuthScopes());
    assertEquals("url", read.getURL());
    assertEquals(true, read.isHTTPS());
  }

  @Test
  void explicitNamesAndRecordComponentsRemainUnchanged() {
    assertEquals("{\"URL\":\"value\"}", mapper.writeValueAsString(new UrlRecord("value")));
    assertEquals("{\"URL\":\"value\"}", mapper.writeValueAsString(new ExplicitBean()));
  }

  @Test
  void annotatedBuilderPrefixUsesLegacyNames() {
    assertEquals(
        "scope", mapper.readValue("{\"oauthScopes\":\"scope\"}", BuiltBean.class).getOAuthScopes());
  }

  static class AcronymBean {
    private String oAuthScopes;
    private String URL;
    private boolean HTTPS;

    public String getOAuthScopes() {
      return oAuthScopes;
    }

    public void setOAuthScopes(String value) {
      oAuthScopes = value;
    }

    public String getURL() {
      return URL;
    }

    public void setURL(String value) {
      URL = value;
    }

    public boolean isHTTPS() {
      return HTTPS;
    }

    public void setHTTPS(boolean value) {
      HTTPS = value;
    }
  }

  record UrlRecord(String URL) {}

  static class ExplicitBean {
    @JsonProperty("URL")
    public String getURL() {
      return "value";
    }
  }

  @JsonDeserialize(builder = BuiltBean.Builder.class)
  static class BuiltBean {
    private final String value;

    BuiltBean(String value) {
      this.value = value;
    }

    public String getOAuthScopes() {
      return value;
    }

    @JsonPOJOBuilder(withPrefix = "assign")
    static class Builder {
      private String value;

      public Builder assignOAuthScopes(String value) {
        this.value = value;
        return this;
      }

      public BuiltBean build() {
        return new BuiltBean(value);
      }
    }
  }
}
