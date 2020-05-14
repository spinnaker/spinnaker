package com.netflix.spinnaker.keel.resources

import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.plugins.SupportedKind

/**
 * A component used to migrate an older version of a [ResourceSpec] to a current one.
 */
interface SpecMigrator<I : ResourceSpec, O : ResourceSpec> {
  val input: SupportedKind<I>
  val output: SupportedKind<O>

  fun migrate(spec: I): O
}

/**
 * Recursively applies [SpecMigrator]s to bring a [spec] of [kind] up to the latest version.
 */
@Suppress("UNCHECKED_CAST")
fun <I : ResourceSpec> Collection<SpecMigrator<*, *>>.migrate(
  kind: ResourceKind,
  spec: I
): Pair<ResourceKind, ResourceSpec> =
  find { it.input.kind == kind }
    ?.let {
      val migrator = it as SpecMigrator<I, *>
      val result = migrator.migrate(spec)
      migrate(migrator.output.kind, result)
    }
    ?: kind to spec
