/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Setter
@Getter
public class ServiceManifest {
  @Nullable private Direct direct;

  @Nullable private String artifactId;

  @Nullable private Artifact artifact;

  public Artifact getArtifact() {
    if (direct != null) {
      return Artifact.builder()
          .name("manifest")
          .type("embedded/base64")
          .artifactAccount("embedded-artifact")
          .reference(Base64.getEncoder().encodeToString(direct.toManifestYml().getBytes()))
          .build();
    }
    return this.artifact;
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class Direct extends DirectManifest {
    @Nullable private String service;
    private boolean updatable = true;
    private boolean versioned = false;

    @JsonAlias("service_instance_name")
    private String serviceInstanceName;

    @Nullable
    @JsonAlias("service_plan")
    private String servicePlan;

    @Nullable private Set<String> tags;

    @Nullable
    @JsonDeserialize(using = OptionallySerializedMapDeserializer.class)
    private Map<String, Object> parameters;

    @Nullable String syslogDrainUrl;

    @Nullable
    @JsonDeserialize(using = OptionallySerializedMapDeserializer.class)
    Map<String, Object> credentials;

    @Nullable String routeServiceUrl;

    @Override
    String toManifestYml() {
      try {
        return manifestMapper.writeValueAsString(this);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Unable to generate Cloud Foundry Manifest", e);
      }
    }
  }

  private static class OptionallySerializedMapDeserializer
      extends JsonDeserializer<Map<String, Object>> {

    private final TypeReference<Map<String, Object>> mapTypeReference =
        new TypeReference<Map<String, Object>>() {};

    private final ObjectMapper yamlObjectMapper = new ObjectMapper(new YAMLFactory());

    @Override
    public Map<String, Object> deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      JsonToken currentToken = parser.currentToken();

      Map<String, Object> deserializedMap = null;

      if (currentToken == JsonToken.START_OBJECT) {
        deserializedMap =
            context.readValue(parser, context.getTypeFactory().constructType(mapTypeReference));
      } else if (currentToken == JsonToken.VALUE_STRING) {
        String serizalizedMap = parser.getValueAsString();
        if (StringUtils.isNotBlank(serizalizedMap)) {
          deserializedMap =
              deserializeWithMappers(
                  serizalizedMap,
                  mapTypeReference,
                  yamlObjectMapper,
                  (ObjectMapper) parser.getCodec());
        }
      }

      return deserializedMap;
    }

    /**
     * Deserialize a String trying with multiple {@link ObjectMapper}.
     *
     * @return The value returned by the first mapper successfully deserializing the input.
     * @throws IOException When all ObjectMappers fail to deserialize the input.
     */
    private <T> T deserializeWithMappers(
        String serialized, TypeReference<T> typeReference, ObjectMapper... mappers)
        throws IOException {

      IOException deserializationFailed =
          new IOException("Could not deserialize value using the provided objectMappers");

      for (ObjectMapper mapper : mappers) {
        try {
          return mapper.readValue(serialized, typeReference);
        } catch (IOException e) {
          deserializationFailed.addSuppressed(e);
        }
      }
      throw deserializationFailed;
    }
  }
}
