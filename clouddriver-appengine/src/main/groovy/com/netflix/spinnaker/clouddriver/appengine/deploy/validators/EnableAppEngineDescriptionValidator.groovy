package com.netflix.spinnaker.clouddriver.appengine.deploy.validators

import com.netflix.spinnaker.clouddriver.appengine.AppEngineOperation
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.EnableDisableAppEngineDescription
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineClusterProvider
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.validation.Errors

@AppEngineOperation(AtomicOperations.ENABLE_SERVER_GROUP)
@Component("enableAppEngineDescriptionValidator")
class EnableAppEngineDescriptionValidator extends DescriptionValidator<EnableDisableAppEngineDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AppEngineClusterProvider appEngineClusterProvider

  @Override
  void validate(List priorDescriptions, EnableDisableAppEngineDescription description, Errors errors) {
    def helper = new StandardAppEngineAttributeValidator("enableAppEngineAtomicOperationDescription", errors)

    helper.validateCredentials(description.accountName, accountCredentialsProvider)
    helper.validateNotEmpty(description.serverGroupName, "serverGroupName")
    helper.validateServerGroupsCanBeEnabled([description.serverGroupName],
                                            null,
                                            description.credentials,
                                            appEngineClusterProvider,
                                            "serverGroupName")
  }
}
