package com.netflix.spinnaker.clouddriver.aws.deploy.validators

import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AttachClassicLinkVpcDescription
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class AttachClassicLinkVpcDescriptionValidatorSpec extends Specification {

  @Shared
  AttachClassicLinkVpcDescriptionValidator validator

  void setupSpec() {
    validator = new AttachClassicLinkVpcDescriptionValidator()
  }

  void "invalid instanceId fails validation"() {
    setup:
    def description = new AttachClassicLinkVpcDescription(vpcId: "vpc-123")
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("instanceId", "AttachClassicLinkVpcDescription.instanceId.invalid")
  }

  void "invalid vpcId fails validation"() {
    setup:
    def description = new AttachClassicLinkVpcDescription(instanceId: "i-123")
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("vpcId", "AttachClassicLinkVpcDescription.vpcId.invalid")
  }

  void "unconfigured region fails validation"() {
    setup:
    def description = new AttachClassicLinkVpcDescription(credentials: TestCredential.named('test'))
    description.region = "us-west-5"
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue("region", "AttachClassicLinkVpcDescription.region.not.configured")

    when:
    description.region = 'us-east-1'
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue("region", "AttachClassicLinkVpcDescription.region.not.configured")
  }
}
