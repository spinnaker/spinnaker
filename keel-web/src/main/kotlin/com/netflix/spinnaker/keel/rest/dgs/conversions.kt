package com.netflix.spinnaker.keel.rest.dgs

import com.netflix.spinnaker.keel.api.AccountAwareLocations
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Locatable
import com.netflix.spinnaker.keel.api.Monikered
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.bakery.diff.PackageDiff
import com.netflix.spinnaker.keel.graphql.types.MdArtifact
import com.netflix.spinnaker.keel.graphql.types.MdCommitInfo
import com.netflix.spinnaker.keel.graphql.types.MdEventLevel
import com.netflix.spinnaker.keel.graphql.types.MdGitMetadata
import com.netflix.spinnaker.keel.graphql.types.MdLocation
import com.netflix.spinnaker.keel.graphql.types.MdMoniker
import com.netflix.spinnaker.keel.graphql.types.MdNotification
import com.netflix.spinnaker.keel.graphql.types.MdPackageAndVersion
import com.netflix.spinnaker.keel.graphql.types.MdPackageAndVersionChange
import com.netflix.spinnaker.keel.graphql.types.MdPackageDiff
import com.netflix.spinnaker.keel.graphql.types.MdPausedInfo
import com.netflix.spinnaker.keel.graphql.types.MdPullRequest
import com.netflix.spinnaker.keel.graphql.types.MdResource
import com.netflix.spinnaker.keel.notifications.DismissibleNotification
import com.netflix.spinnaker.keel.pause.Pause


fun GitMetadata.toDgs(): MdGitMetadata =
  MdGitMetadata(
    commit = commit,
    author = author,
    project = project,
    branch = branch,
    repoName = repo?.name,
    pullRequest = if (pullRequest != null) {
      MdPullRequest(
        number = pullRequest?.number,
        link = pullRequest?.url
      )
    } else null,
    commitInfo = if (commitInfo != null) {
      MdCommitInfo(
        sha = commitInfo?.sha,
        link = commitInfo?.link,
        message = commitInfo?.message
      )
    } else null
  )


fun Resource<*>.toDgs(config: DeliveryConfig, environmentName: String): MdResource =
  MdResource(
    id = id,
    kind = kind.toString(),
    artifact = findAssociatedArtifact(config)?.let { artifact ->
      MdArtifact(
        id = "$environmentName-${artifact.reference}",
        environment = environmentName,
        name = artifact.name,
        type = artifact.type,
        reference = artifact.reference
      )
    },
    displayName = spec.displayName,
    moniker = getMdMoniker(),
    location = (spec as? Locatable<*>)?.let {
      val account = when (val locations = it.locations) {
        is AccountAwareLocations -> locations.account
        is SubnetAwareLocations -> locations.account
        is SimpleLocations -> locations.account
        else -> null
      }
      MdLocation(account = account, regions = it.locations.regions.map { r -> r.name })
    }
  )

fun Resource<*>.getMdMoniker(): MdMoniker? {
  with(spec) {
    return if (this is Monikered) {
      MdMoniker(
        app = moniker.app,
        stack = moniker.stack,
        detail = moniker.detail,
      )
    } else {
      null
    }
  }
}

fun PackageDiff.toDgs() =
  MdPackageDiff(
    added = added.map { (pkg, version) -> MdPackageAndVersion(pkg, version) },
    removed = removed.map { (pkg, version) -> MdPackageAndVersion(pkg, version) },
    changed = changed.map { (pkg, versions) -> MdPackageAndVersionChange(pkg, versions.old, versions.new) }
  )

fun Pause.toDgsPaused(): MdPausedInfo =
  MdPausedInfo(
    id = "$scope-$name-pause",
    by = pausedBy,
    at = pausedAt,
    comment = comment
  )

fun DismissibleNotification.toDgs() =
  MdNotification(
    id = uid?.toString() ?: error("Can't convert application event with missing UID: $this"),
    level = MdEventLevel.valueOf(level.name),
    message = message,
    isActive = isActive,
    triggeredAt = triggeredAt,
    triggeredBy = triggeredBy,
    environment = environment,
    link = link,
    dismissedAt = dismissedAt,
    dismissedBy = dismissedBy
  )
