package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.artifacts.BranchFilter

/**
 * A preview environment (a.k.a. a "feature stack") is an ephemeral test environment created automatically
 * by Keel to provide a means for application owners to test changes to their application's code and
 * infrastructure, in a live environment, without having to merge those changes to the main development branch.
 *
 * Developers create a pull request based on a "feature branch", and the changes pushed to the branch are
 * deployed to the temporary environment. When the PR is finally merged (or declined, or deleted), the
 * temporary environment is cleaned up to avoid wasting resources and to make sure that old versions of
 * code aren't left running unattended as they may impact other environments/systems.
 *
 * The [PreviewEnvironmentSpec] class models the specification provided by users in their [DeliveryConfig] which
 * allows Keel to identify the appropriate artifacts and source branches to monitor.
 */
data class PreviewEnvironmentSpec(
  /**
   * Allows the user to specify a [BranchFilter] for Keel to monitor.
   *
   * For example:
   *
   * ```yaml
   * branch:
   *   startsWith: "feature/"
   * ```
   */
  val branch: BranchFilter,

  /**
   * The name of the static environment upon which preview environments should be based on.
   *
   * Every preview environment deriving from the base environment will include all the resources defined
   * in the base environment. Preview environments will *not* inherit constraints, notifications or
   * verifications from the base environment. Constraints do not apply. Notifications and verifications
   * can be specified explicitly with the keys below.
   */
  val baseEnvironment: String,

  /**
   *  Allows the user to specify notifications for preview environments based on this spec.
   */
  val notifications: Set<NotificationConfig> = emptySet(),

  /**
   * Allows the user to specify verifications for preview environments based on this spec.
   */
  val verifyWith: List<Verification> = emptyList()
) {
  /**
   * Synthetic name used to allow more than one [PreviewEnvironmentSpec] for the same base environment
   * in the [DeliveryConfig].
   */
  val name: String
    get() = "preview/$baseEnvironment/branch=${branch.name ?: branch.startsWith ?: branch.regex}"
}