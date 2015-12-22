package com.netflix.spinnaker.clouddriver.aws.deploy.validators

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AttachClassicLinkVpcDescription
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@AmazonOperation(AtomicOperations.ATTACH_CLASSIC_LINK_VPC)
@Component("attachClassicLinkVpcDescriptionValidator")
class AttachClassicLinkVpcDescriptionValidator extends AmazonDescriptionValidationSupport<AttachClassicLinkVpcDescription> {
  @Override
  void validate(List priorDescriptions, AttachClassicLinkVpcDescription description, Errors errors) {
    def key = AttachClassicLinkVpcDescription.class.simpleName
    if (!description.instanceId) {
      errors.rejectValue("instanceId", "${key}.instanceId.invalid")
    }
    if (!description.vpcId) {
      errors.rejectValue("vpcId", "${key}.vpcId.invalid")
    }
    validateRegion(description, description.region, key, errors)
  }
}
