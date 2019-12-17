package com.netflix.spinnaker.gate.controllers;

import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.netflix.spinnaker.gate.model.manageddelivery.DeliveryConfig;
import com.netflix.spinnaker.gate.model.manageddelivery.Resource;
import com.netflix.spinnaker.gate.services.internal.KeelService;
import groovy.util.logging.Slf4j;
import io.swagger.annotations.ApiOperation;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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
  @GetMapping(path = "/resources/{resourceId}")
  Resource getResource(@PathVariable("resourceId") String resourceId) {
    return keelService.getResource(resourceId);
  }

  @ApiOperation(value = "Get status of a resource", response = Map.class)
  @GetMapping(path = "/resources/{resourceId}/status")
  Map getResourceStatus(@PathVariable("resourceId") String resourceId) {
    Map<String, String> status = new HashMap<>();
    status.put("status", keelService.getResourceStatus(resourceId));
    return status;
  }

  @ApiOperation(value = "Ad-hoc validate and diff a resource", response = Map.class)
  @PostMapping(
      path = "/resources/diff",
      consumes = {APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE},
      produces = {APPLICATION_JSON_VALUE})
  Map diffResource(@RequestBody Resource resource) {
    return keelService.diffResource(resource);
  }

  @ApiOperation(value = "Pause management of a resource")
  @PostMapping(path = "/resources/{resourceId}/pause")
  void pauseResource(@PathVariable("resourceId") String resourceId) {
    keelService.pauseResource(resourceId, Collections.emptyMap());
  }

  @ApiOperation(value = "Resume management of a resource")
  @DeleteMapping(path = "/resources/{resourceId}/pause")
  void resumeResource(@PathVariable("resourceId") String resourceId) {
    keelService.resumeResource(resourceId);
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
  List<Map> diffManifest(@RequestBody DeliveryConfig manifest) {
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

  @ApiOperation(value = "Pause management of an entire application")
  @PostMapping(path = "/application/{application}/pause")
  void pauseApplication(@PathVariable("application") String application) {
    keelService.pauseApplication(application, Collections.emptyMap());
  }

  @ApiOperation(value = "Resume management of an entire application")
  @DeleteMapping(path = "/application/{application}/pause")
  void resumeApplication(@PathVariable("application") String application) {
    keelService.resumeApplication(application);
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
