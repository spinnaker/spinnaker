package com.netflix.spinnaker.mort.aws.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.spinnaker.mort.model.ElasticIp
import groovy.transform.Immutable
import groovy.transform.Sortable

@Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class AmazonElasticIp implements ElasticIp {
  final String type = "aws"
  final String address
  final String domain
  final String attachedToId
  final String accountName
  final String region
}
