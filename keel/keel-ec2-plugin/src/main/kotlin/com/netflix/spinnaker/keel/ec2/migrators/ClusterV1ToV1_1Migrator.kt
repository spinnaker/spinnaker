package com.netflix.spinnaker.keel.ec2.migrators

import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.old.ClusterV1Spec
import com.netflix.spinnaker.keel.resources.SpecMigrator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("keel.plugins.ec2.enabled")
class ClusterV1ToV1_1Migrator : SpecMigrator<ClusterV1Spec, ClusterSpec> {
  @Suppress("DEPRECATION")
  override val input = EC2_CLUSTER_V1
  override val output = EC2_CLUSTER_V1_1

  override fun migrate(spec: ClusterV1Spec): ClusterSpec =
    ClusterSpec(
      moniker = spec.moniker,
      artifactReference = spec.imageProvider?.reference,
      deployWith = spec.deployWith,
      locations = spec.locations,
      _defaults = spec.defaults,
      overrides = spec.overrides
    )
}
