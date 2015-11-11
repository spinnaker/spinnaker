package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.clouddriver.pipeline.UpsertLoadBalancerStage
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

/**
 * @deprecated use {@link com.netflix.spinnaker.orca.clouddriver.pipeline.UpsertLoadBalancerStage} instead.
 */
@Deprecated
@Component
@CompileStatic
class UpsertAmazonLoadBalancerStage extends UpsertLoadBalancerStage {

  public static final String PIPELINE_CONFIG_TYPE = "upsertAmazonLoadBalancer"

  UpsertAmazonLoadBalancerStage() {
    super(PIPELINE_CONFIG_TYPE)
  }
}
