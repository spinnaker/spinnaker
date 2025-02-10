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
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS;
import static com.fasterxml.jackson.databind.DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleAbstractTypeResolver;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger;
import com.netflix.spinnaker.orca.jackson.mixin.PipelineExecutionMixin;
import com.netflix.spinnaker.orca.jackson.mixin.StageExecutionMixin;
import com.netflix.spinnaker.orca.jackson.mixin.TriggerMixin;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.TaskExecutionImpl;
import java.io.IOException;
import org.springframework.http.HttpMethod;

public class OrcaObjectMapper {
  private OrcaObjectMapper() {}

  private static final ObjectMapper INSTANCE = newInstance();

  public static ObjectMapper newInstance() {
    ObjectMapper instance = new ObjectMapper();
    instance.registerModule(new Jdk8Module());
    instance.registerModule(new GuavaModule());
    instance.registerModule(new JavaTimeModule());
    instance.registerModule(new KotlinModule.Builder().build());
    instance.disable(READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
    instance.disable(WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
    instance.disable(FAIL_ON_UNKNOWN_PROPERTIES);
    instance.enable(READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
    instance.setSerializationInclusion(NON_NULL);

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

    instance.registerModule(module);

    SimpleModule httpMethodModule = new SimpleModule();
    httpMethodModule.addSerializer(HttpMethod.class, new HttpMethodSerializer());
    httpMethodModule.addDeserializer(HttpMethod.class, new HttpMethodDeserializer());
    instance.registerModule(httpMethodModule);

    return instance;
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

  static class HttpMethodSerializer extends JsonSerializer<HttpMethod> {
    @Override
    public void serialize(HttpMethod value, JsonGenerator gen, SerializerProvider serializer)
        throws IOException {
      gen.writeString(value.name().toUpperCase());
    }
  }

  static class HttpMethodDeserializer extends JsonDeserializer<HttpMethod> {
    @Override
    public HttpMethod deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      return HttpMethod.valueOf(p.getText().toUpperCase());
    }
  }
}
