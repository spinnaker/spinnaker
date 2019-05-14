/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.validation;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.validation.exception.StageValidationException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StageValidator {
  private static final Logger log = LoggerFactory.getLogger(StageValidator.class);

  private final ObjectMapper objectMapper;
  private final JsonSchemaFactory jsonSchemaFactory;
  private final String schemaRoot;

  @Autowired
  public StageValidator(ObjectMapper objectMapper) {
    this(objectMapper, "/schemas/");
  }

  StageValidator(ObjectMapper objectMapper, String schemaRoot) {
    this.objectMapper = objectMapper;
    this.jsonSchemaFactory = JsonSchemaFactory.byDefault();

    // support overriding the schema root (primarily for test cases)
    this.schemaRoot = (schemaRoot + "/").replaceAll("//", "/");
  }

  public boolean isValid(Stage stage) {
    Optional<Schema> schema = loadSchema(stage);
    if (!schema.isPresent()) {
      return true;
    }

    JsonSchema jsonSchema = processSchema(schema.get());

    try {
      JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(stage.getContext()));
      ProcessingReport processingReport = jsonSchema.validate(json);

      if (!processingReport.isSuccess()) {
        log.info(
            "Failed to validate stage (executionId: {}, stageId: {}), reason: {}",
            stage.getExecution().getId(),
            stage.getId(),
            processingReport);
      }

      return processingReport.isSuccess();
    } catch (ProcessingException | IOException e) {
      throw new StageValidationException(e);
    }
  }

  /**
   * Load schema and exclude any conditional properties that are not enabled for the current stage's
   * cloud provider.
   */
  private Optional<Schema> loadSchema(Stage stage) {
    String stageType = stage.getType();
    Optional<String> cloudProvider = getCloudProvider(stage);

    try {
      URL schemaUrl = StageValidator.class.getResource(schemaRoot + stageType + ".json");
      if (schemaUrl == null) {
        // schema does not exist
        return Optional.empty();
      }

      Schema schema =
          objectMapper
              .readerWithView(Views.Spinnaker.class)
              .withType(Schema.class)
              .readValue(new File(schemaUrl.toURI()));

      schema.properties =
          schema.properties.entrySet().stream()
              .filter(
                  e -> {
                    // filter out any conditional property that does not support `cloudProvider`
                    Collection<String> cloudProviders = e.getValue().meta.condition.cloudProviders;
                    return cloudProviders == null
                        || cloudProviders.contains(cloudProvider.orElse(null));
                  })
              .map(
                  e -> {
                    // ensure that all schema properties support a 'string' value in order to
                    // support arbitrary SpEL expression use
                    Schema.Property property = e.getValue();
                    if (property.type != null && !property.type.equalsIgnoreCase("string")) {
                      property.anyOf =
                          Arrays.asList(
                              Collections.singletonMap("type", property.type),
                              Collections.singletonMap("type", "string"));
                      property.type = null;
                    }
                    return e;
                  })
              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

      schema.required.addAll(
          schema.properties.entrySet().stream()
              .filter(e -> e.getValue().meta.required)
              .map(Map.Entry::getKey)
              .collect(Collectors.toSet()));

      return Optional.of(schema);
    } catch (Exception e) {
      throw new StageValidationException(e);
    }
  }

  private JsonSchema processSchema(Schema schema) {
    try {
      return jsonSchemaFactory.getJsonSchema(
          objectMapper.readTree(
              objectMapper.writerWithView(Views.Public.class).writeValueAsString(schema)));
    } catch (Exception e) {
      throw new StageValidationException(e);
    }
  }

  private static Optional<String> getCloudProvider(Stage stage) {
    Map context = Optional.ofNullable(stage.getContext()).orElse(new HashMap<>());
    if (context.containsKey("cloudProvider")) {
      return Optional.of((String) context.get("cloudProvider"));
    }

    if (context.containsKey("cloudProviderType")) {
      return Optional.of((String) context.get("cloudProviderType"));
    }

    return Optional.empty();
  }

  private static class Generic {
    private final Map<String, Object> any = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> any() {
      return any;
    }

    @JsonAnySetter
    public void setAny(String name, Object value) {
      any.put(name, value);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private static class Schema extends Generic {
    @JsonProperty Map<String, Property> properties;

    @JsonProperty Set<String> required = new HashSet<>();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class Property extends Generic {
      @JsonProperty String type;

      @JsonProperty List<Map> anyOf;

      @JsonProperty("_meta")
      @JsonView(Views.Spinnaker.class)
      Meta meta = new Meta();

      static class Meta {
        @JsonProperty boolean required;

        @JsonProperty boolean builtIn;

        @JsonProperty Condition condition = new Condition();
      }

      static class Condition extends Generic {
        @JsonProperty Set<String> cloudProviders;
      }
    }
  }

  private static class Views {
    static class Public {}

    static class Spinnaker {}
  }
}
