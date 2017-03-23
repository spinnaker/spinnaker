package com.netflix.spinnaker.orca.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

public class OrcaObjectMapper {
  private OrcaObjectMapper() {}

  public static ObjectMapper newInstance() {
    ObjectMapper instance = new ObjectMapper();
    instance.registerModule(new Jdk8Module());
    instance.registerModule(new GuavaModule());
    instance.disable(FAIL_ON_UNKNOWN_PROPERTIES);
    instance.setSerializationInclusion(NON_NULL);
    return instance;
  }
}
