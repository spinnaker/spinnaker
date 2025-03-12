/*
 * Copyright 2022 Apple Inc.
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

package com.netflix.spinnaker.credentials.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.fasterxml.jackson.databind.ser.std.StringSerializer;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import java.io.IOException;
import java.util.regex.Matcher;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.jackson.JsonComponent;

/**
 * Sensitive string serializer for Jackson. Used to help prevent accidental leakage of sensitive
 * secrets. Properties annotated with {@link Sensitive} or having a likely-sensitive property name
 * inside a {@link CredentialsDefinition}-derived class will be replaced with {@code null} during
 * serialization.
 */
@Log4j2
@JsonComponent
public class SensitiveSerializer extends StdScalarSerializer<String>
    implements ContextualSerializer {

  private final StringSerializer defaultStringSerializer = new StringSerializer();
  private final SensitiveProperties properties;

  public SensitiveSerializer(SensitiveProperties properties) {
    super(String.class);
    this.properties = properties;
  }

  @Override
  public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
      throws JsonMappingException {
    if (property == null) {
      return defaultStringSerializer;
    }
    Sensitive sensitive = property.getAnnotation(Sensitive.class);
    if (sensitive != null) {
      return this;
    }
    Class<?> declaringClass = property.getMember().getDeclaringClass();
    if (CredentialsDefinition.class.isAssignableFrom(declaringClass)) {
      String name = property.getName();
      Matcher matcher = properties.getSensitivePropertyNamePattern().matcher(name);
      if (matcher.matches()) {
        log.warn(
            "Encountered likely sensitive property name '{}.{}'. Ignoring this property for serialization.",
            declaringClass.getName(),
            name);
        return this;
      }
    }
    return defaultStringSerializer;
  }

  @Override
  public void serialize(String value, JsonGenerator gen, SerializerProvider provider)
      throws IOException {
    if (value.startsWith("secret://")
        || value.startsWith("encrypted:")
        || value.startsWith("encryptedFile:")) {
      gen.writeString(value);
    } else {
      gen.writeNull();
    }
  }
}
