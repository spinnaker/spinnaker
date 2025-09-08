package com.netflix.spinnaker.cats.pubsub.controllers;

import com.netflix.spinnaker.cats.pubsub.StateMachine;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnProperty("pubsub.scheduler.enabled")
@RestController
@RequestMapping("/admin/scheduler")
public class PubSubAdminController {
  @Autowired StateMachine stateMachine;

  @GetMapping("/agents")
  public List<StateMachine.AgentState> getAgents() {
    return stateMachine.listAgentsFilteredWhereIn(null);
  }

  @DeleteMapping("/agents/{agentType}")
  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  public void deleteAgent(String agentType) {
    stateMachine.delete(agentType);
  }
}
