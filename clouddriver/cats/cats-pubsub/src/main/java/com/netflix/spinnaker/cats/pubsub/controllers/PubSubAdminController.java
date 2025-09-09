package com.netflix.spinnaker.cats.pubsub.controllers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.netflix.spinnaker.cats.pubsub.StateMachine;

import java.io.IOException;
import java.text.DateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnProperty("cats.pubsub.enabled")
@RestController
@RequestMapping("/admin/scheduler")
public class PubSubAdminController extends JsonSerializer<Long> {
  @Autowired StateMachine stateMachine;
  private ObjectMapper mapper;

  @Autowired
  public void setObjectMapper(ObjectMapper mapper) {
    this.mapper = mapper.copy();
    SimpleModule simpleModule = new SimpleModule("convertLongsToDates");
    simpleModule.addSerializer(long.class, this);
    this.mapper.registerModule(simpleModule);
  }

  @GetMapping("/agents")
  public String getAgents() throws Exception {
    return mapper.writeValueAsString(stateMachine.listAgentsFilteredWhereIn(null));
  }

  @DeleteMapping("/agents/{agentType}")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public void deleteAgent(String agentType) {
    stateMachine.delete(agentType);
  }

  @Override
  public void serialize(Long aLong, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
    // assume anything THIS big is a date stamp :) Makes reading via the API a bit simpler
    if (aLong > 100000) {
      jsonGenerator.writeString(DateTimeFormatter.ISO_DATE_TIME.format(    LocalDateTime.ofInstant(Instant.ofEpochMilli(aLong), ZoneId.systemDefault())));
    } else {
      jsonGenerator.writeString(aLong.toString());
    }
  }
}
