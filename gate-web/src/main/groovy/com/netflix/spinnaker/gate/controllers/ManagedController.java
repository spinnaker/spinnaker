package com.netflix.spinnaker.gate.controllers;

import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.netflix.spinnaker.gate.services.internal.KeelService;
import com.netflix.spinnaker.kork.manageddelivery.model.DeliveryConfig;
import com.netflix.spinnaker.kork.manageddelivery.model.Resource;
import groovy.util.logging.Slf4j;
import io.swagger.annotations.ApiOperation;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import retrofit.RetrofitError;
import retrofit.client.Header;

@RequestMapping("/managed")
@RestController
@Slf4j
@ConditionalOnProperty("services.keel.enabled")
public class ManagedController {

  private final HttpHeaders yamlResponseHeaders;
  private static final Logger log = LoggerFactory.getLogger(ManagedController.class);
  private final KeelService keelService;
  private final String APPLICATION_YAML_VALUE = "application/x-yaml";

  @Autowired
  public ManagedController(KeelService keelService) {
    this.keelService = keelService;
    this.yamlResponseHeaders = new HttpHeaders();
    yamlResponseHeaders.setContentType(
        new MediaType("application", "x-yaml", StandardCharsets.UTF_8));
  }

  @ApiOperation(value = "Get a resource", response = Resource.class)
  @GetMapping(path = "/resources/{name}")
  Resource getResource(@PathVariable("name") String name) {
    return keelService.getResource(name);
  }

  @ApiOperation(value = "Get status of a resource", response = Map.class)
  @GetMapping(path = "/resources/{name}/status")
  Map getResourceStatus(@PathVariable("name") String name) {
    Map<String, String> status = new HashMap<>();
    status.put("status", keelService.getResourceStatus(name));
    return status;
  }

  @ApiOperation(value = "Create or update a resource", response = Resource.class)
  @PostMapping(path = "/resources")
  Resource upsertResource(@RequestBody Resource resource) {
    return keelService.upsertResource(resource);
  }

  @ApiOperation(value = "Ad-hoc validate and diff a resource", response = Map.class)
  @PostMapping(
      path = "/resources/diff",
      consumes = {APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE},
      produces = {APPLICATION_JSON_VALUE})
  Map diffResource(@RequestBody Resource resource) {
    return keelService.diffResource(resource);
  }

  @ApiOperation(value = "Delete a resource", response = Resource.class)
  @DeleteMapping(path = "/resources/{name}")
  Resource deleteResource(@PathVariable("name") String name) {
    return keelService.deleteResource(name);
  }

  @ApiOperation(
      value = "Generate a keel resource definition for a deployed cloud resource",
      response = Resource.class)
  @GetMapping(path = "/resources/export/{cloudProvider}/{account}/{type}/{name}")
  ResponseEntity<Resource> exportResource(
      @PathVariable("cloudProvider") String cloudProvider,
      @PathVariable("account") String account,
      @PathVariable("type") String type,
      @PathVariable("name") String name,
      @RequestParam("serviceAccount") String serviceAccount) {
    Resource resource =
        keelService.exportResource(cloudProvider, account, type, name, serviceAccount);
    return new ResponseEntity<>(resource, yamlResponseHeaders, HttpStatus.OK);
  }

  @ApiOperation(value = "Get a delivery config manifest", response = DeliveryConfig.class)
  @GetMapping(path = "/delivery-configs/{name}")
  DeliveryConfig getManifest(@PathVariable("name") String name) {
    return keelService.getManifest(name);
  }

  @ApiOperation(
      value = "Create or update a delivery config manifest",
      response = DeliveryConfig.class)
  @PostMapping(path = "/delivery-configs")
  DeliveryConfig upsertManifest(@RequestBody DeliveryConfig manifest) {
    return keelService.upsertManifest(manifest);
  }

  @ApiOperation(value = "Ad-hoc validate and diff a config manifest", response = Map.class)
  @PostMapping(
      path = "/delivery-configs/diff",
      consumes = {APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE},
      produces = {APPLICATION_JSON_VALUE})
  Map diffManifest(@RequestBody DeliveryConfig manifest) {
    return keelService.diffManifest(manifest);
  }

  @ApiOperation(value = "Get managed details about an application", response = Map.class)
  @GetMapping(path = "/application/{application}")
  Map getApplicationDetails(
      @PathVariable("application") String application,
      @RequestParam(value = "includeDetails", required = false, defaultValue = "false")
          Boolean includeDetails) {
    return keelService.getApplicationDetails(application, includeDetails);
  }

  @ApiOperation(value = "Pass a message to a veto plugin", response = Map.class)
  @PostMapping(path = "/vetos/{name}")
  void passVetoMessage(
      @PathVariable("name") String name, @RequestBody Map<String, Object> message) {
    keelService.passVetoMessage(name, message);
  }

  @ApiOperation(value = "Get everything a specific veto plugin will reject", response = List.class)
  @GetMapping(path = "/vetos/{name}/rejections")
  List<String> getVetoRejections(@PathVariable("name") String name) {
    return keelService.getVetoRejections(name);
  }

  @ExceptionHandler
  void passthroughRetrofitErrors(RetrofitError e, HttpServletResponse response) {
    try {
      response.setStatus(e.getResponse().getStatus());
      response.setHeader(
          CONTENT_TYPE,
          e.getResponse().getHeaders().stream()
              .filter(it -> it.getName().equals(CONTENT_TYPE))
              .map(Header::getValue)
              .findFirst()
              .orElse("text/plain"));
      IOUtils.copy(e.getResponse().getBody().in(), response.getOutputStream());
    } catch (Exception ex) {
      log.error(
          "Error reading response body when translating exception from downstream keelService: ",
          ex);
    }
  }
}
