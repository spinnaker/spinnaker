package com.netflix.spinnaker.gate.controllers;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.model.manageddelivery.ConstraintState;
import com.netflix.spinnaker.gate.model.manageddelivery.ConstraintStatus;
import com.netflix.spinnaker.gate.model.manageddelivery.DeliveryConfig;
import com.netflix.spinnaker.gate.model.manageddelivery.EnvironmentArtifactPin;
import com.netflix.spinnaker.gate.model.manageddelivery.EnvironmentArtifactVeto;
import com.netflix.spinnaker.gate.model.manageddelivery.OverrideVerificationRequest;
import com.netflix.spinnaker.gate.model.manageddelivery.Resource;
import com.netflix.spinnaker.gate.model.manageddelivery.RetryVerificationRequest;
import com.netflix.spinnaker.gate.services.NotificationService;
import com.netflix.spinnaker.gate.services.internal.KeelService;
import com.netflix.spinnaker.kork.web.interceptors.Criticality;
import groovy.util.logging.Slf4j;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.swagger.annotations.ApiOperation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import retrofit.RetrofitError;
import retrofit.client.Header;
import retrofit.client.Response;

@Criticality(Criticality.Value.LOW)
@RequestMapping("/managed")
@RestController
@Slf4j
@ConditionalOnProperty("services.keel.enabled")
public class ManagedController {

  private static final Logger log = LoggerFactory.getLogger(ManagedController.class);
  private static final String APPLICATION_YAML_VALUE = "application/x-yaml";

  private final HttpHeaders yamlResponseHeaders;
  private final KeelService keelService;
  private final ObjectMapper objectMapper;
  private final RetryRegistry retryRegistry;
  private final NotificationService notificationService;

  @Autowired
  public ManagedController(
      KeelService keelService,
      ObjectMapper objectMapper,
      RetryRegistry retryRegistry,
      NotificationService notificationService) {
    this.keelService = keelService;
    this.objectMapper = objectMapper;
    this.yamlResponseHeaders = new HttpHeaders();
    this.retryRegistry = retryRegistry;
    this.notificationService = notificationService;
    yamlResponseHeaders.setContentType(
        new MediaType("application", "x-yaml", StandardCharsets.UTF_8));

    configureRetry();
  }

  private void configureRetry() {
    // TODO(rz): Wire up kork to look in `classpath*:resilience4j-defaults.yml` for service-defined
    //  defaults rather than doing in-code configuration which is less flexible for end-users.
    //  These will probably be fine, though.
    retryRegistry.addConfiguration(
        "managed-write",
        RetryConfig.custom()
            .maxAttempts(5)
            .waitDuration(Duration.ofSeconds(30))
            .retryExceptions(RetrofitError.class)
            .build());
  }

  @ApiOperation(value = "Get a resource", response = Resource.class)
  @GetMapping(path = "/resources/{resourceId}")
  Resource getResource(@PathVariable("resourceId") String resourceId) {
    return keelService.getResource(resourceId);
  }

  @ApiOperation(value = "Get a resource", response = Resource.class)
  @GetMapping(path = "/resources/{resourceId}.yml", produces = APPLICATION_YAML_VALUE)
  Resource getResourceYaml(@PathVariable("resourceId") String resourceId) {
    return keelService.getResourceYaml(resourceId);
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

  @ApiOperation(
      value = "Generates an artifact definition based on the artifact used in a running cluster",
      response = Map.class)
  @GetMapping(path = "/resources/export/artifact/{cloudProvider}/{account}/{clusterName}")
  ResponseEntity<Map> exportResource(
      @PathVariable("cloudProvider") String cloudProvider,
      @PathVariable("account") String account,
      @PathVariable("clusterName") String clusterName) {
    Map<String, Object> artifact = keelService.exportArtifact(cloudProvider, account, clusterName);
    return new ResponseEntity<>(artifact, yamlResponseHeaders, HttpStatus.OK);
  }

  @ApiOperation(value = "Get a delivery config manifest", response = DeliveryConfig.class)
  @GetMapping(path = "/delivery-configs/{name}")
  DeliveryConfig getManifest(@PathVariable("name") String name) {
    return keelService.getManifest(name);
  }

  @ApiOperation(value = "Get a delivery config manifest", response = DeliveryConfig.class)
  @GetMapping(path = "/delivery-configs/{name}.yml", produces = APPLICATION_YAML_VALUE)
  DeliveryConfig getManifestYaml(@PathVariable("name") String name) {
    return keelService.getManifestYaml(name);
  }

  @ApiOperation(
      value = "Get the status of each version of each artifact in each environment",
      response = List.class)
  @GetMapping(path = "/delivery-configs/{name}/artifacts")
  List<Map<String, Object>> getManifestArtifacts(@PathVariable("name") String name) {
    return keelService.getManifestArtifacts(name);
  }

  @SneakyThrows
  @ApiOperation(
      value = "Create or update a delivery config manifest",
      response = DeliveryConfig.class)
  @PostMapping(
      path = "/delivery-configs",
      consumes = {APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE},
      produces = {APPLICATION_JSON_VALUE})
  DeliveryConfig upsertManifest(@RequestBody DeliveryConfig manifest) {
    return retryRegistry
        .retry("managed-write")
        .executeCallable(() -> keelService.upsertManifest(manifest));
  }

  @ApiOperation(value = "Delete a delivery config manifest", response = DeliveryConfig.class)
  @DeleteMapping(path = "/delivery-configs/{name}")
  DeliveryConfig deleteManifest(@PathVariable("name") String name) {
    return keelService.deleteManifest(name);
  }

  @ApiOperation(value = "Validate a delivery config manifest", response = Map.class)
  @PostMapping(
      path = "/delivery-configs/validate",
      consumes = {APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE},
      produces = {APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE})
  ResponseEntity<Map> validateManifest(@RequestBody DeliveryConfig manifest) {
    try {
      return ResponseEntity.ok(keelService.validateManifest(manifest));
    } catch (RetrofitError e) {
      if (e.getResponse().getStatus() == 400) {
        try {
          return ResponseEntity.badRequest()
              .body(objectMapper.readValue(e.getResponse().getBody().in(), Map.class));
        } catch (Exception ex) {
          log.error("Error parsing error response from keel", ex);
          return ResponseEntity.badRequest().body(Collections.emptyMap());
        }
      } else {
        throw e;
      }
    }
  }

  @ApiOperation(value = "Ad-hoc validate and diff a config manifest", response = Map.class)
  @PostMapping(
      path = "/delivery-configs/diff",
      consumes = {APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE},
      produces = {APPLICATION_JSON_VALUE})
  List<Map> diffManifest(@RequestBody DeliveryConfig manifest) {
    return keelService.diffManifest(manifest);
  }

  @ApiOperation(value = "Ad-hoc validate and diff a config manifest", response = Map.class)
  @GetMapping(
      path = "/delivery-configs/schema",
      produces = {APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE})
  Map<String, Object> schema() {
    return keelService.schema();
  }

  @ApiOperation(
      value = "List up-to {limit} current constraint states for an environment",
      response = ConstraintState.class)
  @GetMapping(path = "/application/{application}/environment/{environment}/constraints")
  List<ConstraintState> getConstraintState(
      @PathVariable("application") String application,
      @PathVariable("environment") String environment,
      @RequestParam(value = "limit", required = false, defaultValue = "10") String limit) {
    return keelService.getConstraintState(application, environment, Integer.valueOf(limit));
  }

  @ApiOperation(
      value = "Get the delivery config associated with an application",
      response = DeliveryConfig.class)
  @GetMapping(path = "/application/{application}/config")
  DeliveryConfig getConfigBy(@PathVariable("application") String application) {
    return keelService.getConfigBy(application);
  }

  @ApiOperation(
      value = "Delete a delivery config manifest for an application",
      response = DeliveryConfig.class)
  @DeleteMapping(path = "/application/{application}/config")
  DeliveryConfig deleteManifestByApp(@PathVariable("application") String application) {
    return keelService.deleteManifestByAppName(application);
  }

  @ApiOperation(value = "Update the status of an environment constraint")
  @PostMapping(path = "/application/{application}/environment/{environment}/constraint")
  void updateConstraintStatus(
      @PathVariable("application") String application,
      @PathVariable("environment") String environment,
      @RequestBody ConstraintStatus status) {
    keelService.updateConstraintStatus(application, environment, status);
  }

  @ApiOperation(value = "Get managed details about an application", response = Map.class)
  @GetMapping(path = "/application/{application}")
  Map getApplicationDetails(
      @PathVariable("application") String application,
      @RequestParam(name = "includeDetails", required = false, defaultValue = "false")
          Boolean includeDetails,
      @RequestParam(name = "entities", required = false, defaultValue = "resources")
          List<String> entities,
      @RequestParam(name = "maxArtifactVersions", required = false) Integer maxArtifactVersions) {
    return keelService.getApplicationDetails(
        application, includeDetails, entities, maxArtifactVersions);
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

  @ApiOperation(value = "Create a pin for an artifact in an environment")
  @PostMapping(path = "/application/{application}/pin")
  void createPin(
      @PathVariable("application") String application, @RequestBody EnvironmentArtifactPin pin) {
    keelService.pin(application, pin);
  }

  @ApiOperation(
      value =
          "Unpin one or more artifact(s) in an environment. If the `reference` parameter is specified, only "
              + "the corresponding artifact will be unpinned. If it's omitted, all pinned artifacts in the environment will be "
              + "unpinned.")
  @DeleteMapping(path = "/application/{application}/pin/{targetEnvironment}")
  void deletePin(
      @PathVariable("application") String application,
      @PathVariable("targetEnvironment") String targetEnvironment,
      @RequestParam(value = "reference", required = false) String reference) {
    keelService.deletePinForEnvironment(application, targetEnvironment, reference);
  }

  @ApiOperation(value = "Veto an artifact version in an environment")
  @PostMapping(path = "/application/{application}/veto")
  void veto(
      @PathVariable("application") String application, @RequestBody EnvironmentArtifactVeto veto) {
    keelService.veto(application, veto);
  }

  @ApiOperation(value = "Remove veto of an artifact version in an environment")
  @DeleteMapping(path = "/application/{application}/veto/{targetEnvironment}/{reference}/{version}")
  void deleteVeto(
      @PathVariable("application") String application,
      @PathVariable("targetEnvironment") String targetEnvironment,
      @PathVariable("reference") String reference,
      @PathVariable("version") String version) {
    keelService.deleteVeto(application, targetEnvironment, reference, version);
  }

  @ApiOperation(value = "Veto an artifact version in an environment")
  @PostMapping(path = "/application/{application}/mark/bad")
  void markBad(
      @PathVariable("application") String application, @RequestBody EnvironmentArtifactVeto veto) {
    keelService.markBad(application, veto);
  }

  @ApiOperation(value = "Delete veto of an artifact version in an environment")
  @PostMapping(path = "/application/{application}/mark/good")
  void markGood(
      @PathVariable("application") String application, @RequestBody EnvironmentArtifactVeto veto) {
    keelService.markGood(application, veto);
  }

  @ApiOperation(value = "Override the status of a verification")
  @PostMapping(path = "/{application}/environment/{environment}/verifications")
  void overrideVerification(
      @PathVariable("application") String application,
      @PathVariable("environment") String environment,
      @RequestBody OverrideVerificationRequest payload) {
    keelService.overrideVerification(application, environment, payload);
  }

  @ApiOperation(value = "Retry a verification")
  @PostMapping(path = "/{application}/environment/{environment}/verifications/retry")
  void retryVerification(
      @PathVariable("application") String application,
      @PathVariable("environment") String environment,
      @PathVariable("verificationId") String verificationId,
      @RequestBody RetryVerificationRequest payload) {
    keelService.retryVerification(application, environment, payload);
  }

  @PostMapping(
      path = "/notifications/callbacks/{source}",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<String> processNotificationCallback(
      @PathVariable String source, RequestEntity<String> request) {
    return notificationService.processNotificationCallback(source, request, "keel");
  }

  @ApiOperation(value = "Get a report of application onboarding")
  @GetMapping(path = "/reports/onboarding")
  ResponseEntity<byte[]> getOnboardingReport(
      @RequestHeader(value = "Accept", defaultValue = "text/html") String accept,
      @RequestParam Map<String, String> params)
      throws IOException {
    Response keelResponse = keelService.getOnboardingReport(accept, params);

    ResponseEntity response =
        ResponseEntity.status(keelResponse.getStatus())
            .header(
                "Content-Type",
                keelResponse.getHeaders().stream()
                    .filter(header -> header.getName().equalsIgnoreCase("Content-Type"))
                    .findFirst()
                    .orElse(new Header("Content-Type", accept))
                    .getValue())
            .body(keelResponse.getBody().in().readAllBytes());

    return response;
  }

  @ApiOperation(value = "Get a report of Managed Delivery adoption")
  @GetMapping(path = "/reports/adoption", produces = "text/html")
  ResponseEntity<byte[]> getAdoptionReport(@RequestParam Map<String, String> params)
      throws IOException {
    Response keelResponse = keelService.getAdoptionReport(params);
    return ResponseEntity.status(keelResponse.getStatus())
        .header("Content-Type", "text/html")
        .body(keelResponse.getBody().in().readAllBytes());
  }

  @ApiOperation(value = "Get current environment details")
  @GetMapping(path = "/environments/{application}", produces = MediaType.APPLICATION_JSON_VALUE)
  List<Map<String, Object>> getEnvironments(@PathVariable String application) {
    return keelService.getEnvironments(application);
  }
}
