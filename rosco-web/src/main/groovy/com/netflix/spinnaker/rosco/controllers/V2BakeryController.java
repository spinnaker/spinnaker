package com.netflix.spinnaker.rosco.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.manifests.BakeManifestRequest;
import com.netflix.spinnaker.rosco.manifests.BakeManifestService;
import groovy.util.logging.Slf4j;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class V2BakeryController {
  private final List<BakeManifestService> bakeManifestServices;
  ObjectMapper objectMapper = new ObjectMapper();

  public V2BakeryController(List<BakeManifestService> bakeManifestServices) {
    this.bakeManifestServices = bakeManifestServices;
  }

  @RequestMapping(value = "/api/v2/manifest/bake/{type}", method = RequestMethod.POST)
  Artifact doBake(@PathVariable("type") String type, @RequestBody Map<String, Object> request) {
    BakeManifestService service =
        bakeManifestServices.stream()
            .filter(s -> s.handles(type))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Cannot bake manifest with template renderer type: " + type));

    BakeManifestRequest bakeManifestRequest =
        (BakeManifestRequest) objectMapper.convertValue(request, service.requestType());

    return service.bake(bakeManifestRequest);
  }
}
