package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.services.AdminService
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.slf4j.LoggerFactory.getLogger
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/admin"])
class AdminController(
  private val adminService: AdminService
) {
  private val log by lazy { getLogger(javaClass) }

  @DeleteMapping(
    path = ["/applications/{application}"]
  )
  @ResponseStatus(NO_CONTENT)
  fun deleteApplicationData(
    @PathVariable("application") application: String
  ) {
    adminService.deleteApplicationData(application)
  }

  @GetMapping(
    path = ["/applications/paused"]
  )
  fun getPausedApplications() =
    adminService.getPausedApplications()

  @GetMapping(
    path = ["/applications"],
    produces = [MediaType.APPLICATION_JSON_VALUE, APPLICATION_YAML_VALUE]
  )
  fun getManagedApplications() =
    adminService.getManagedApplications()

  @PostMapping(
    path = ["/recheck/{resourceId}"]
  )
  fun triggerRecheck(resourceId: String) {
    adminService.triggerRecheck(resourceId)
  }
}
