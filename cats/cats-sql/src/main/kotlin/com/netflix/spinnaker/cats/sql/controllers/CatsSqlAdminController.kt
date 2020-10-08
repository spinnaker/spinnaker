package com.netflix.spinnaker.cats.sql.controllers

import com.netflix.spinnaker.cats.sql.cache.SqlAdminCommandsRepository
import com.netflix.spinnaker.cats.sql.cache.SqlSchemaVersion
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// TODO: Replace validatePermissions() with a to-be-implemented fiat isAdmin() decorator

@ConditionalOnProperty("sql.cache.enabled")
@RestController
@RequestMapping("/admin/db")
class CatsSqlAdminController(
  private val fiat: FiatPermissionEvaluator,
  private val adminCommands: SqlAdminCommandsRepository
) {

  companion object {
    private val log by lazy { LoggerFactory.getLogger(CatsSqlAdminController::class.java) }
  }

  @PutMapping(path = ["/truncate/{namespace}"])
  fun truncateTables(
    @PathVariable("namespace") truncateNamespace: String,
    @Value("\${sql.table-namespace:#{null}}") currentNamespace: String?
  ): CleanTablesResult {

    validatePermissions()
    validateNamespaceParams(currentNamespace, truncateNamespace)

    val tablesTruncated = adminCommands.truncateTablesByNamespace(truncateNamespace)
    return CleanTablesResult(tableCount = tablesTruncated.size, tables = tablesTruncated)
  }

  @PutMapping(path = ["/drop/{namespace}"])
  fun dropTables(
    @PathVariable("namespace") dropNamespace: String,
    @Value("\${sql.table-namespace:#{null}}") currentNamespace: String?
  ): CleanTablesResult {

    validatePermissions()
    validateNamespaceParams(currentNamespace, dropNamespace)

    val tablesDropped = adminCommands.dropTablesByNamespace(dropNamespace)
    return CleanTablesResult(tableCount = tablesDropped.size, tables = tablesDropped)
  }

  @PutMapping(path = ["drop_version/{version}"])
  fun dropTablesByVersion(
    @PathVariable("version") dropVersion: SqlSchemaVersion
  ): CleanTablesResult {

    validatePermissions()

    val tablesDropped = adminCommands.dropTablesByVersion(dropVersion)
    return CleanTablesResult(tableCount = tablesDropped.size, tables = tablesDropped)
  }

  private fun validateNamespaceParams(currentNamespace: String?, targetNamespace: String) {
    if (currentNamespace == null) {
      throw IllegalStateException("truncate can only be called when sql.tableNamespace is set")
    }

    if (!targetNamespace.matches("""^\w+$""".toRegex())) {
      throw IllegalArgumentException("tableNamespace can only contain characters [a-z, A-Z, 0-9, _]")
    }

    if (currentNamespace.toLowerCase() == targetNamespace.toLowerCase()) {
      throw IllegalArgumentException("truncate cannot be called for the currently active namespace")
    }
  }

  private fun validatePermissions() {
    val user = AuthenticatedRequest.getSpinnakerUser()
    if (!user.isPresent) {
      throw BadCredentialsException("Unauthorized")
    }

    try {
      val perms = fiat.getPermission(user.get())
      if (!perms.isAdmin) {
        throw BadCredentialsException("Unauthorized")
      }
    } catch (e: Exception) {
      log.error("Failed looking up fiat permissions for user ${user.get()}")
      throw BadCredentialsException("Unauthorized", e)
    }
  }
}

data class CleanTablesResult(
  val tableCount: Int,
  val tables: Collection<String>
)
