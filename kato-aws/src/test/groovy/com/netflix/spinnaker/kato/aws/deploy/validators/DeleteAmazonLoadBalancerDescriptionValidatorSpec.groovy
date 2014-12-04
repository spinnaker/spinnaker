/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kato.aws.deploy.validators
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.amos.aws.NetflixAssumeRoleAmazonCredentials
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.DeleteAmazonLoadBalancerDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DeleteAmazonLoadBalancerDescriptionValidatorSpec extends Specification {
  @Subject
    validator = new DeleteAmazonLoadBalancerDescriptionValidator()

  @Shared
  AmazonClientProvider amazonClientProvider = Mock(AmazonClientProvider)

  @Shared
  AmazonElasticLoadBalancing loadBalancing = Mock(AmazonElasticLoadBalancing)

  void setup() {
    amazonClientProvider = Mock(AmazonClientProvider)
    loadBalancing = Mock(AmazonElasticLoadBalancing)
    validator.amazonClientProvider = amazonClientProvider
  }

  void "should fail validation with invalid load balancer"() {
    setup:
    def errors = Mock(Errors)
    def description = new DeleteAmazonLoadBalancerDescription(loadBalancerName: "foo--frontend", regions: ["us-east-1"], credentials: Mock(NetflixAssumeRoleAmazonCredentials))
    validator.amazonClientProvider = amazonClientProvider

    when:
    validator.validate([], description, errors)

    then:
    1 * amazonClientProvider.getAmazonElasticLoadBalancing(_, "us-east-1") >> loadBalancing
    1 * loadBalancing.describeLoadBalancers(_) >> { DescribeLoadBalancersRequest req ->
      assert req.loadBalancerNames[0] == description.loadBalancerName
      new DescribeLoadBalancersResult(loadBalancerDescriptions: [])
    }
    1 * errors.rejectValue("name", *_)
  }

  void "region is validates against configuration"() {
    setup:
    def creds = TestCredential.named('test')
    def description = new DeleteAmazonLoadBalancerDescription(loadBalancerName: "foo--frontend", credentials: creds)
    description.regions = ["us-east-5"]
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("regions", _)

    and:
    1 * amazonClientProvider.getAmazonElasticLoadBalancing(_, _) >> loadBalancing
    1 * loadBalancing.describeLoadBalancers(_) >> { DescribeLoadBalancersRequest req ->
      assert req.loadBalancerNames[0] == description.loadBalancerName
      new DescribeLoadBalancersResult(loadBalancerDescriptions: [])
    }

    when:
    description.regions = ['us-west-1', 'us-east-1']
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue("regions", _)

    and:
    2 * amazonClientProvider.getAmazonElasticLoadBalancing(_, _) >> loadBalancing
    2 * loadBalancing.describeLoadBalancers(_) >> { DescribeLoadBalancersRequest req ->
      assert req.loadBalancerNames[0] == description.loadBalancerName
      new DescribeLoadBalancersResult(loadBalancerDescriptions: [])
    }
  }
}
