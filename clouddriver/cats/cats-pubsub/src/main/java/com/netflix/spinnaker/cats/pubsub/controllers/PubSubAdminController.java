package com.netflix.spinnaker.cats.pubsub.controllers;

import com.netflix.spinnaker.cats.pubsub.StateMachine;
import com.netflix.spinnaker.kork.annotations.Alpha;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

@ConditionalOnProperty("cats.pubsub.enabled")
@RestController
@RequestMapping("/admin/scheduler")
@Alpha
public class PubSubAdminController extends ValueSerializer<Long> {
  @Autowired StateMachine stateMachine;
  private ObjectMapper mapper;

  @Autowired
  public void setObjectMapper(ObjectMapper mapper) {
    SimpleModule simpleModule = new SimpleModule("convertLongsToDates");
    simpleModule.addSerializer(long.class, this);
    this.mapper = mapper.rebuild().addModule(simpleModule).build();
  }

  @GetMapping("/agents")
  public String getAgents() throws Exception {
    return mapper.writeValueAsString(stateMachine.listAgentsFilteredWhereIn(null));
  }

  @DeleteMapping("/agents/{agentType}")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public void deleteAgent(@PathVariable String agentType) {
    stateMachine.delete(agentType);
  }

  @Override
  public void serialize(Long aLong, JsonGenerator jsonGenerator, SerializationContext serializers)
      throws JacksonException {
    // assume anything THIS big is a date stamp :) Makes reading via the API a bit simpler
    if (aLong > 100000) {
      jsonGenerator.writeString(
          DateTimeFormatter.ISO_DATE_TIME.format(
              LocalDateTime.ofInstant(Instant.ofEpochMilli(aLong), ZoneId.systemDefault())));
    } else {
      jsonGenerator.writeString(aLong.toString());
    }
  }
}
