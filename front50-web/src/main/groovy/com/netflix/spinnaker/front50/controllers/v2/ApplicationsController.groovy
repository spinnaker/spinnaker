package com.netflix.spinnaker.front50.controllers.v2

import com.netflix.spinnaker.fiat.shared.FiatService
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.front50.ServiceAccountsService
import com.netflix.spinnaker.front50.config.FiatConfigurationProperties
import com.netflix.spinnaker.front50.controllers.exception.InvalidApplicationRequestException
import com.netflix.spinnaker.front50.events.ApplicationEventListener
import com.netflix.spinnaker.front50.exception.ApplicationAlreadyExistsException
import com.netflix.spinnaker.front50.exception.NotFoundException
import com.netflix.spinnaker.front50.exception.ValidationException
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO
import com.netflix.spinnaker.front50.model.application.ApplicationService
import com.netflix.spinnaker.front50.model.notification.NotificationDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineStrategyDAO
import com.netflix.spinnaker.front50.model.project.ProjectDAO
import com.netflix.spinnaker.front50.validator.ApplicationValidator
import groovy.util.logging.Slf4j
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.Errors
import org.springframework.validation.ObjectError
import org.springframework.web.bind.annotation.*

import javax.servlet.http.HttpServletResponse

@Slf4j
@RestController
@RequestMapping("/v2/applications")
@Api(value = "application", description = "Application API")
public class ApplicationsController {
  @Autowired
  MessageSource messageSource

  @Autowired
  ApplicationDAO applicationDAO

  @Autowired(required = false)
  ApplicationPermissionDAO applicationPermissionDAO

  @Autowired
  ProjectDAO projectDAO

  @Autowired
  NotificationDAO notificationDAO

  @Autowired
  PipelineDAO pipelineDAO

  @Autowired
  PipelineStrategyDAO pipelineStrategyDAO

  @Autowired
  List<ApplicationValidator> applicationValidators

  @Autowired(required = false)
  List<ApplicationEventListener> applicationEventListeners = []

  @Autowired
  Optional<FiatService> fiatService;

  @Autowired
  Optional<ServiceAccountsService> serviceAccountsService;

  @Autowired
  FiatConfigurationProperties fiatConfigurationProperties;

  @Autowired
  FiatStatus fiatStatus;

  @Autowired
  ApplicationService applicationService;

  @PreAuthorize("#restricted ? @fiatPermissionEvaluator.storeWholePermission() : true")
  @PostFilter("#restricted ? hasPermission(filterObject.name, 'APPLICATION', 'READ') : true")
  @ApiOperation(value = "", notes = """Fetch all applications.

    Supports filtering by one or more attributes:
    - ?email=my@email.com
    - ?email=my@email.com&name=flex""")
  @RequestMapping(method = RequestMethod.GET)
  Set<Application> applications(@RequestParam(value = "pageSize", required = false) Integer pageSize,
                                @RequestParam(required = false, value = 'restricted', defaultValue = 'true') boolean restricted,
                                @RequestParam Map<String, String> params) {
    params.remove("pageSize")
    params.remove("restricted")

    def applications
    def permissions = applicationPermissionDAO ? applicationPermissionDAO.all()
      .findAll { it.permissions.isRestricted() }
      .groupBy { it.name.toLowerCase() } : [:]
    if (params.isEmpty()) {
      applications = applicationDAO.all().sort { it.name }
    } else {
      applications = applicationDAO.search(params)
    }

    Set<Application> results = pageSize ? applications.asList().subList(0, Math.min(pageSize, applications.size())) : applications
    results.each { application ->
      if (permissions.containsKey(application.name.toLowerCase())) {
        application.set("permissions", permissions.get(application.name.toLowerCase())[0].permissions)
      } else {
        application.details().remove("permissions")
      }
    }
    return results
  }

  @PreAuthorize("@fiatPermissionEvaluator.canCreate('APPLICATION', #app)")
  @ApiOperation(value = "", notes = "Create an application")
  @RequestMapping(method = RequestMethod.POST)
  Application create(@RequestBody final Application app) {
    if (applicationService.findByName(app.getName()) != null) {
      throw new ApplicationAlreadyExistsException();
    }

    Application createdApplication = applicationService.save(app);
    if (fiatStatus.isEnabled() && fiatConfigurationProperties.getRoleSync().isEnabled() && fiatService.isPresent()) {
      try {
        fiatService.get().sync()
      } catch (Exception ignored) {
        log.warn("failed to trigger fiat permission sync", ignored)
      }
    }
    return createdApplication
  }

  @PreAuthorize("hasPermission(#applicationName, 'APPLICATION', 'WRITE')")
  @ApiOperation(value = "", notes = "Delete an application")
  @RequestMapping(method = RequestMethod.DELETE, value = "/{applicationName:.+}")
  void delete(@PathVariable String applicationName, HttpServletResponse response) {
    applicationService.delete(applicationName)
    response.setStatus(HttpStatus.NO_CONTENT.value())
  }

  @PreAuthorize("hasPermission(#app.name, 'APPLICATION', 'WRITE')")
  @ApiOperation(value = "", notes = "Update an existing application by merging the attributes")
  @RequestMapping(method = RequestMethod.PATCH, value = "/{applicationName:.+}")
  Application update(@PathVariable String applicationName, @RequestBody final Application app) {
    if (!applicationName.trim().equalsIgnoreCase(app.getName())) {
      throw new InvalidApplicationRequestException("Application name '${app.getName()}' does not match path parameter '${applicationName}'")
    }
    return applicationService.save(app)
  }

  @PreAuthorize("hasPermission(#app.name, 'APPLICATION', 'WRITE')")
  @ApiOperation(value = "", notes = "Update an existing application by replacing all attributes")
  @RequestMapping(method = RequestMethod.PUT, value = "/{applicationName:.+}")
  Application replace(@PathVariable String applicationName, @RequestBody final Application app) {
    if (!applicationName.trim().equalsIgnoreCase(app.getName())) {
      throw new InvalidApplicationRequestException("Application name '${app.getName()}' does not match path parameter '${applicationName}'")
    }

    return applicationService.replace(app);
  }

  // This method uses @PostAuthorize in order to throw 404s if the application doesn't exist,
  // vs. 403s if the app exists, but the user doesn't have access to it.
  @PostAuthorize("hasPermission(#applicationName, 'APPLICATION', 'READ')")
  @ApiOperation(value = "", notes = "Fetch a single application by name")
  @RequestMapping(method = RequestMethod.GET, value = "/{applicationName:.+}")
  Application get(@PathVariable final String applicationName) {
    def app = applicationDAO.findByName(applicationName.toUpperCase())
    try {
      def perm = applicationPermissionDAO?.findById(app.name)
      if (perm?.permissions?.isRestricted()) {
        app.details().put("permissions", perm.permissions)
      } else {
        app.details().remove("permissions")
      }
    } catch (NotFoundException nfe) {
      // ignored.
    }
    return app
  }

  @PreAuthorize("hasPermission(#applicationName, 'APPLICATION', 'READ')")
  @RequestMapping(value = '{applicationName:.+}/history', method = RequestMethod.GET)
  Collection<Application> getHistory(@PathVariable String applicationName,
                                     @RequestParam(value = "limit", defaultValue = "20") int limit) {
    return applicationDAO.history(applicationName, limit)
  }

  @PreAuthorize("@fiatPermissionEvaluator.isAdmin()")
  @RequestMapping(method = RequestMethod.POST, value = "/batch/applications")
  void batchUpdate(@RequestBody final Collection<Application> applications) {
    applicationDAO.bulkImport(applications)
  }

  @ExceptionHandler(ValidationException)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  Map handleValidationException(ValidationException ex) {
    def locale = LocaleContextHolder.locale
    def errorStrings = []
    ex.errors.each { Errors errors ->
      errors.allErrors.each { ObjectError objectError ->
        def message = messageSource.getMessage(objectError.code, objectError.arguments, objectError.defaultMessage, locale)
        errorStrings << message
      }
    }
    return [error: "Validation Failed.", errors: errorStrings, status: HttpStatus.BAD_REQUEST]
  }
}
