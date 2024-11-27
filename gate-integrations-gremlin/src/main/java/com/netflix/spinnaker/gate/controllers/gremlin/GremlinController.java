package com.netflix.spinnaker.gate.controllers.gremlin;

import com.netflix.spinnaker.gate.services.gremlin.GremlinService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integrations/gremlin")
@ConditionalOnProperty("integrations.gremlin.enabled")
class GremlinController {
  private static final String APIKEY_KEY = "apiKey";

  private final GremlinService gremlinService;

  @Autowired
  public GremlinController(GremlinService gremlinService) {
    this.gremlinService = gremlinService;
  }

  @Operation(summary = "Retrieve a list of gremlin command templates")
  @RequestMapping(value = "/templates/command", method = RequestMethod.POST)
  List listCommandTemplates(@RequestBody(required = true) Map apiKeyMap) {
    String apiKeyValue = (String) apiKeyMap.get(APIKEY_KEY);
    return gremlinService.getCommandTemplates("Key " + apiKeyValue);
  }

  @Operation(summary = "Retrieve a list of gremlin target templates")
  @RequestMapping(value = "/templates/target", method = RequestMethod.POST)
  List listTargetTemplates(@RequestBody(required = true) Map apiKeyMap) {
    String apiKeyValue = (String) apiKeyMap.get(APIKEY_KEY);
    return gremlinService.getTargetTemplates("Key " + apiKeyValue);
  }
}
