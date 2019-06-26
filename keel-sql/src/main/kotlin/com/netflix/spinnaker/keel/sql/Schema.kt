package com.netflix.spinnaker.keel.sql

import org.jooq.Field
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL
import org.jooq.impl.DSL.table
import java.sql.Timestamp

internal sealed class Schema {
  abstract val table: Table<Record>

  protected inline fun <reified T> field(name: String): Field<T> =
    DSL.field("${table.name}.$name", T::class.java)

  internal object DeliveryConfig : Schema() {
    override val table: Table<Record> = table("delivery_config")

    val uid = field<String>("uid")
    val name = field<String>("name")
    val application = field<String>("application")
  }

  internal object DeliveryConfigArtifact : Schema() {
    override val table: Table<Record> = table("delivery_config_artifact")

    val deliveryConfigUid = field<String>("delivery_config_uid")
    val artifactUid = field<String>("artifact_uid")
  }

  internal object Artifact : Schema() {
    override val table: Table<Record> = table("delivery_artifact")

    val uid = field<String>("uid")
    val name = field<String>("name")
    val type = field<String>("type")
  }

  internal object Environment : Schema() {
    override val table: Table<Record> = table("environment")

    val uid = field<String>("uid")
    val deliveryConfigUid = field<String>("delivery_config_uid")
    val name = field<String>("name")
  }

  internal object EnvironmentResource : Schema() {
    override val table: Table<Record> = table("environment_resource")

    val environmentUid = field<String>("environment_uid")
    val resourceUid = field<String>("resource_uid")
  }

  internal object Resource : Schema() {
    override val table: Table<Record> = table("resource")

    val uid = field<String>("uid")
    val apiVersion = field<String>("api_version")
    val kind = field<String>("kind")
    val name = field<String>("name")
    val spec = field<String>("spec")
    val metadata = field<String>("metadata")
    val lastChecked = field<Timestamp>("last_checked")
  }
}
