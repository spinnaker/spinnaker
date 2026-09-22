/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.jackson;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static tools.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES;
import static tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static tools.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS;
import static tools.jackson.databind.MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS;
import static tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY;
import static tools.jackson.databind.MapperFeature.USE_GETTERS_AS_SETTERS;
import static tools.jackson.databind.cfg.DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS;
import static tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS;
import static tools.jackson.databind.cfg.DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS;
import static tools.jackson.databind.cfg.EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE;

import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger;
import com.netflix.spinnaker.orca.config.JacksonParserProperties;
import com.netflix.spinnaker.orca.jackson.mixin.PipelineExecutionMixin;
import com.netflix.spinnaker.orca.jackson.mixin.StageExecutionMixin;
import com.netflix.spinnaker.orca.jackson.mixin.TriggerMixin;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.TaskExecutionImpl;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.Version;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleAbstractTypeResolver;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.datatype.guava.GuavaModule;
import tools.jackson.module.kotlin.KotlinModule;

public class OrcaObjectMapper {
  private OrcaObjectMapper() {}

  private static final ObjectMapper INSTANCE = newInstance();

  public static ObjectMapper newInstance() {
    return newInstance(new JacksonParserProperties());
  }

  public static ObjectMapper newInstance(JacksonParserProperties parserProperties) {
    return newInstance(parserProperties, Optional.empty());
  }

  public static ObjectMapper newInstance(
      JacksonParserProperties parserProperties,
      Optional<? extends ValueSerializerModifier> serializerModifier) {
    StreamReadConstraints constraints =
        StreamReadConstraints.builder()
            .maxNameLength(parserProperties.getMaxNameLength())
            .maxStringLength(parserProperties.getMaxStringLength())
            .maxNestingDepth(parserProperties.getMaxNestingDepth())
            .maxNumberLength(parserProperties.getMaxNumberLength())
            .maxDocumentLength(parserProperties.getMaxDocumentLength())
            .build();

    JsonMapper.Builder builder =
        JsonMapper.builder(JsonFactory.builder().streamReadConstraints(constraints).build())
            .addModule(new GuavaModule())
            .addModule(new KotlinModule.Builder().build())
            .disable(READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .disable(WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
            .enable(WRITE_DATES_AS_TIMESTAMPS)
            .disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(SORT_PROPERTIES_ALPHABETICALLY)
            .enable(READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
            .enable(ACCEPT_CASE_INSENSITIVE_ENUMS)
            .disable(USE_GETTERS_AS_SETTERS)
            .enable(ALLOW_FINAL_FIELDS_AS_MUTATORS)
            .changeDefaultPropertyInclusion(value -> value.withValueInclusion(NON_NULL));

    // Jackson cannot deserialize an interface. For interfaces defined by orca-api, we need to tell
    // Jackson the singular class that implement these interfaces.
    SimpleModule module = new SimpleModule("apiTypes", Version.unknownVersion());
    SimpleAbstractTypeResolver resolver = new SimpleAbstractTypeResolver();
    resolver.addMapping(TaskExecution.class, TaskExecutionImpl.class);
    resolver.addMapping(StageExecution.class, StageExecutionImpl.class);
    resolver.addMapping(PipelineExecution.class, PipelineExecutionImpl.class);
    module.setMixInAnnotation(Trigger.class, TriggerMixin.class);
    module.setMixInAnnotation(StageExecution.class, StageExecutionMixin.class);
    module.setMixInAnnotation(PipelineExecution.class, PipelineExecutionMixin.class);
    module.setAbstractTypes(resolver);

    builder.addModule(module);

    // Custom (de)serializers added to ensure HttpMethod values are always uppercase.
    // This restores the behavior that existed before Spring Boot 3 upgrade:
    // previously, HttpMethod values were serialized as uppercase globally.
    // Using a custom serializer/deserializer ensures consistency for all JSON
    // (de)serialization across Orca, including tests and persisted payloads.
    SimpleModule httpMethodModule = new SimpleModule();
    httpMethodModule.addSerializer(HttpMethod.class, new HttpMethodSerializer());
    httpMethodModule.addDeserializer(HttpMethod.class, new HttpMethodDeserializer());
    httpMethodModule.addDeserializer(HttpStatusCode.class, new HttpStatusCodeDeserializer());
    builder.addModule(httpMethodModule);

    serializerModifier.ifPresent(
        modifier -> builder.addModule(new SimpleModule().setSerializerModifier(modifier)));

    return builder.build();
  }

  /**
   * Return an ObjectMapper instance that can be reused. Do not change the configuration of this
   * instance as it will be shared across the entire application, use {@link #newInstance()}
   * instead.
   *
   * @return Reusable ObjectMapper instance
   */
  public static ObjectMapper getInstance() {
    return INSTANCE;
  }

  /**
   * Custom Jackson serializer for {@link HttpMethod}.
   *
   * <p>Converts the enum value to an uppercase string (e.g., {@code GET}).
   */
  static class HttpMethodSerializer extends ValueSerializer<HttpMethod> {
    @Override
    public void serialize(HttpMethod value, JsonGenerator gen, SerializationContext serializers)
        throws JacksonException {
      gen.writeString(value.name().toUpperCase());
    }
  }

  /**
   * Custom Jackson deserializer for {@link HttpMethod}.
   *
   * <p>Converts JSON strings (e.g., {@code get}, {@code Get}, {@code GET}) into uppercase before
   * resolving the corresponding {@link HttpMethod}.
   */
  static class HttpMethodDeserializer extends StdDeserializer<HttpMethod> {
    HttpMethodDeserializer() {
      super(HttpMethod.class);
    }

    @Override
    public HttpMethod deserialize(JsonParser p, DeserializationContext ctxt)
        throws JacksonException {
      return HttpMethod.valueOf(p.getText().toUpperCase());
    }
  }

  /**
   * Custom Jackson deserializer for {@link org.springframework.http.HttpStatusCode}.
   *
   * <p>Spring Framework 6 introduced {@code HttpStatusCode} as a numeric abstraction over HTTP
   * status values. Unlike {@link org.springframework.http.HttpStatus}, it does not support symbolic
   * string names (e.g. {@code "OK"}, {@code "CREATED"}) during deserialization.
   *
   * <p>This deserializer exists to maintain backward compatibility with previously persisted
   * execution context data where HTTP status codes were stored as strings (for example, {@code
   * "OK"}), while still supporting numeric representations (e.g. {@code 200}).
   *
   * <p>The deserializer supports the following input formats:
   *
   * <ul>
   *   <li>Numeric status codes: {@code 200}
   *   <li>String enum names: {@code "OK"}, {@code "NOT_FOUND"}
   *   <li>Numeric strings: {@code "200"}
   * </ul>
   *
   * <p>All valid inputs are normalized to {@link HttpStatusCode#valueOf(int)}.
   */
  static class HttpStatusCodeDeserializer extends StdDeserializer<HttpStatusCode> {
    HttpStatusCodeDeserializer() {
      super(HttpStatusCode.class);
    }

    @Override
    public HttpStatusCode deserialize(JsonParser p, DeserializationContext ctxt)
        throws JacksonException {

      JsonNode node = ctxt.readTree(p);

      // Case 1: numeric status code (200)
      if (node.isInt()) {
        return HttpStatusCode.valueOf(node.intValue());
      }

      // Case 2: string status code ("OK")
      if (node.isTextual()) {
        String text = node.textValue();

        try {
          // Try enum name (OK, CREATED, etc.)
          HttpStatus status = HttpStatus.valueOf(text);
          return HttpStatusCode.valueOf(status.value());
        } catch (IllegalArgumentException ignored) {
          // fall through
        }

        // Try numeric string ("200")
        try {
          return HttpStatusCode.valueOf(Integer.parseInt(text));
        } catch (NumberFormatException ex) {
          throw ctxt.weirdStringException(
              text, HttpStatusCode.class, "Unrecognized HTTP status code");
        }
      }

      throw new SpinnakerException("Cannot deserialize HttpStatusCode from " + node);
    }
  }
}
