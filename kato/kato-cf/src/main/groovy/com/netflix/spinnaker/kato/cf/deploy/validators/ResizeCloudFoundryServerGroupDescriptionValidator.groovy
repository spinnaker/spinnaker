package com.netflix.spinnaker.kato.cf.deploy.validators
import com.netflix.spinnaker.clouddriver.cf.CloudFoundryOperation
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.cf.deploy.description.ResizeCloudFoundryServerGroupDescription
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import com.netflix.spinnaker.kato.orchestration.AtomicOperations
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@CloudFoundryOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component("resizeCloudFoundryServerGroupDescriptionValidator")
class ResizeCloudFoundryServerGroupDescriptionValidator extends DescriptionValidator<ResizeCloudFoundryServerGroupDescription> {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, ResizeCloudFoundryServerGroupDescription description, Errors errors) {
    def helper = new StandardCfAttributeValidator("resizeCloudFoundryServerGroupDescription", errors)

    helper.validateCredentials(description.accountName, accountCredentialsProvider)
    helper.validateServerGroupName(description.serverGroupName)
    helper.validateZone(description.zone)
    helper.validatePositiveInt(description.targetSize, "targetSize")
  }
}
