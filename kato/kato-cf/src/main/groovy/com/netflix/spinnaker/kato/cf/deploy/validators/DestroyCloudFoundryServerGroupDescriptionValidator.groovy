package com.netflix.spinnaker.kato.cf.deploy.validators

import com.netflix.spinnaker.clouddriver.cf.CloudFoundryOperation
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.cf.deploy.description.DestroyCloudFoundryServerGroupDescription
import com.netflix.spinnaker.kato.deploy.DescriptionValidator
import com.netflix.spinnaker.kato.orchestration.AtomicOperations
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@CloudFoundryOperation(AtomicOperations.DESTROY_SERVER_GROUP)
@Component("destroyCloudFoundryServerGroupDescriptionValidator")
class DestroyCloudFoundryServerGroupDescriptionValidator extends DescriptionValidator<DestroyCloudFoundryServerGroupDescription> {

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Override
  void validate(List priorDescriptions, DestroyCloudFoundryServerGroupDescription description, Errors errors) {
    def helper = new StandardCfAttributeValidator("destroyCloudFoundryServerGroupDescription", errors)

    helper.validateCredentials(description.accountName, accountCredentialsProvider)
    helper.validateServerGroupName(description.serverGroupName)
    helper.validateZone(description.zone)

  }
}
