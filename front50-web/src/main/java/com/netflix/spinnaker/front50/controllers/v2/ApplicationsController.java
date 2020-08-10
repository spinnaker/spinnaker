package com.netflix.spinnaker.front50.controllers.v2;

import static java.lang.String.format;

import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.front50.config.FiatConfigurationProperties;
import com.netflix.spinnaker.front50.controllers.exception.InvalidApplicationRequestException;
import com.netflix.spinnaker.front50.exception.ApplicationAlreadyExistsException;
import com.netflix.spinnaker.front50.exception.ValidationException;
import com.netflix.spinnaker.front50.model.application.Application;
import com.netflix.spinnaker.front50.model.application.ApplicationDAO;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.application.ApplicationService;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.*;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v2/applications")
@Api(value = "application", description = "Application API")
public class ApplicationsController {

  private static final Logger log = LoggerFactory.getLogger(ApplicationsController.class);

  private final MessageSource messageSource;
  private final ApplicationDAO applicationDAO;
  private final Optional<ApplicationPermissionDAO> applicationPermissionDAO;
  private final Optional<FiatService> fiatService;
  private final FiatConfigurationProperties fiatConfigurationProperties;
  private final FiatStatus fiatStatus;
  private final ApplicationService applicationService;

  public ApplicationsController(
      MessageSource messageSource,
      ApplicationDAO applicationDAO,
      Optional<ApplicationPermissionDAO> applicationPermissionDAO,
      Optional<FiatService> fiatService,
      FiatConfigurationProperties fiatConfigurationProperties,
      FiatStatus fiatStatus,
      ApplicationService applicationService) {
    this.messageSource = messageSource;
    this.applicationDAO = applicationDAO;
    this.applicationPermissionDAO = applicationPermissionDAO;
    this.fiatService = fiatService;
    this.fiatConfigurationProperties = fiatConfigurationProperties;
    this.fiatStatus = fiatStatus;
    this.applicationService = applicationService;
  }

  @PreAuthorize("#restricted ? @fiatPermissionEvaluator.storeWholePermission() : true")
  @PostFilter("#restricted ? hasPermission(filterObject.name, 'APPLICATION', 'READ') : true")
  @ApiOperation(
      value = "",
      notes =
          "Fetch all applications.\n\nSupports filtering by one or more attributes:\n- ?email=my@email.com\n- ?email=my@email.com&name=flex")
  @RequestMapping(method = RequestMethod.GET)
  public Set<Application> applications(
      @RequestParam(value = "pageSize", required = false) Integer pageSize,
      @RequestParam(required = false, value = "restricted", defaultValue = "true")
          boolean restricted,
      @RequestParam Map<String, String> params) {
    params.remove("pageSize");
    params.remove("restricted");

    Map<String, List<Application.Permission>> permissions =
        applicationPermissionDAO
            .map(
                apd ->
                    apd.all().stream()
                        .filter(it -> it.getPermissions().isRestricted())
                        .collect(Collectors.groupingBy(it -> it.getName().toLowerCase())))
            .orElseGet(HashMap::new);

    List<Application> applications;
    if (params.isEmpty()) {
      applications =
          applicationDAO.all().stream()
              .sorted(Comparator.comparing(Application::getName))
              .collect(Collectors.toList());
    } else {
      applications = new ArrayList<>(applicationDAO.search(params));
    }

    Set<Application> results =
        new HashSet<>(
            pageSize == null
                ? applications
                : applications.subList(0, Math.min(pageSize, applications.size())));
    results.forEach(
        it -> {
          if (permissions.containsKey(it.getName().toLowerCase())) {
            it.set(
                "permissions", permissions.get(it.getName().toLowerCase()).get(0).getPermissions());
          } else {
            it.details().remove("permissions");
          }
        });

    return results;
  }

  @PreAuthorize("@fiatPermissionEvaluator.canCreate('APPLICATION', #app)")
  @ApiOperation(value = "", notes = "Create an application")
  @RequestMapping(method = RequestMethod.POST)
  public Application create(@RequestBody final Application app) {
    if (applicationService.findByName(app.getName()) != null) {
      throw new ApplicationAlreadyExistsException();
    }

    Application createdApplication = applicationService.save(app);
    if (fiatStatus.isEnabled()
        && fiatConfigurationProperties.getRoleSync().isEnabled()
        && fiatService.isPresent()) {
      try {
        fiatService.get().sync();
      } catch (Exception e) {
        log.warn("failed to trigger fiat permission sync", e);
      }
    }

    return createdApplication;
  }

  @PreAuthorize("hasPermission(#applicationName, 'APPLICATION', 'WRITE')")
  @ApiOperation(value = "", notes = "Delete an application")
  @RequestMapping(method = RequestMethod.DELETE, value = "/{applicationName:.+}")
  public void delete(@PathVariable String applicationName, HttpServletResponse response) {
    applicationService.delete(applicationName);
    response.setStatus(HttpStatus.NO_CONTENT.value());
  }

  @PreAuthorize("hasPermission(#app.name, 'APPLICATION', 'WRITE')")
  @ApiOperation(value = "", notes = "Update an existing application by merging the attributes")
  @RequestMapping(method = RequestMethod.PATCH, value = "/{applicationName:.+}")
  public Application update(
      @PathVariable final String applicationName, @RequestBody final Application app) {
    if (!applicationName.trim().equalsIgnoreCase(app.getName())) {
      throw new InvalidApplicationRequestException(
          format(
              "Application name '%s' does not match path parameter '%s'",
              app.getName(), applicationName));
    }

    return applicationService.save(app);
  }

  @PreAuthorize("hasPermission(#app.name, 'APPLICATION', 'WRITE')")
  @ApiOperation(value = "", notes = "Update an existing application by replacing all attributes")
  @RequestMapping(method = RequestMethod.PUT, value = "/{applicationName:.+}")
  public Application replace(
      @PathVariable final String applicationName, @RequestBody final Application app) {
    if (!applicationName.trim().equalsIgnoreCase(app.getName())) {
      throw new InvalidApplicationRequestException(
          format(
              "Application name '%s' does not match path parameter '%s'",
              app.getName(), applicationName));
    }

    return applicationService.replace(app);
  }

  @PostAuthorize("hasPermission(#applicationName, 'APPLICATION', 'READ')")
  @ApiOperation(value = "", notes = "Fetch a single application by name")
  @RequestMapping(method = RequestMethod.GET, value = "/{applicationName:.+}")
  public Application get(@PathVariable final String applicationName) {
    Application app = applicationDAO.findByName(applicationName.toUpperCase());

    try {
      Application.Permission perm =
          applicationPermissionDAO.map(it -> it.findById(app.getName())).orElse(null);
      if (perm != null && perm.getPermissions().isRestricted()) {
        app.details().put("permissions", perm.getPermissions());
      } else {
        app.details().remove("permissions");
      }
    } catch (NotFoundException nfe) {
      // ignored.
    }

    return app;
  }

  @PreAuthorize("hasPermission(#applicationName, 'APPLICATION', 'READ')")
  @RequestMapping(value = "{applicationName:.+}/history", method = RequestMethod.GET)
  public Collection<Application> getHistory(
      @PathVariable String applicationName,
      @RequestParam(value = "limit", defaultValue = "20") int limit) {
    return applicationDAO.history(applicationName, limit);
  }

  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  @RequestMapping(method = RequestMethod.POST, value = "/batch/applications")
  public void batchUpdate(@RequestBody final Collection<Application> applications) {
    applicationDAO.bulkImport(applications);
  }

  @ExceptionHandler(ValidationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map handleValidationException(ValidationException ex) {
    final Locale locale = LocaleContextHolder.getLocale();
    final List<String> errorStrings =
        ex.getErrors().getAllErrors().stream()
            .map(
                it ->
                    messageSource.getMessage(
                        Optional.ofNullable(it.getCode()).orElse("-1"),
                        it.getArguments(),
                        it.getDefaultMessage(),
                        locale))
            .collect(Collectors.toList());

    LinkedHashMap<String, Object> map = new LinkedHashMap<>(3);
    map.put("error", "Validation Failed.");
    map.put("errors", errorStrings);
    map.put("status", HttpStatus.BAD_REQUEST);
    return map;
  }
}
