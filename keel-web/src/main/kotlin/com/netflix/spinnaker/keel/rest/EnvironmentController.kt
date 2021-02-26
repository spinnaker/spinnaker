package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.services.EnvironmentService
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/environments"])
class EnvironmentController(
  private val environmentService: EnvironmentService
) {

  @GetMapping(
    path = ["/{application}"],
    produces = [APPLICATION_JSON_VALUE]
  )
  @PreAuthorize(
    """@authorizationSupport.hasApplicationPermission('READ', 'APPLICATION', #application)
    and @authorizationSupport.hasCloudAccountPermission('READ', 'APPLICATION', #application)"""
  )
  fun list(@PathVariable("application") application: String) =
    environmentService.getEnvironmentViews(application)
}
