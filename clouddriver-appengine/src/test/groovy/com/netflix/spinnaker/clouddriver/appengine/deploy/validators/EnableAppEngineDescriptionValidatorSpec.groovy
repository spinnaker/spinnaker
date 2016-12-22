package com.netflix.spinnaker.clouddriver.appengine.deploy.validators

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.EnableDisableAppEngineDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineServerGroup
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineCredentials
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class EnableAppEngineDescriptionValidatorSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final REGION = "us-central"
  private static final APPLICATION_NAME = "myapp"

  private static final SERVER_GROUP_NAME = "app-stack-detail-v000"
  private static final SERVER_GROUP = new AppEngineServerGroup(
    loadBalancers: [LOAD_BALANCER_NAME]
  )
  private static final LOAD_BALANCER_NAME = "default"

  @Shared
  AppEngineNamedAccountCredentials credentials

  @Shared
  DefaultAccountCredentialsProvider accountCredentialsProvider

  void setupSpec() {
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def mockCredentials = Mock(AppEngineCredentials)
    credentials = new AppEngineNamedAccountCredentials.Builder()
      .name(ACCOUNT_NAME)
      .region(REGION)
      .applicationName(APPLICATION_NAME)
      .credentials(mockCredentials)
      .build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    accountCredentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
  }

  void "fails validation if server group cannot be found"() {
    setup:
      def validator = new EnableAppEngineDescriptionValidator()
      validator.accountCredentialsProvider = accountCredentialsProvider
      validator.appEngineClusterProvider = Mock(AppEngineClusterProvider)

      def description = new EnableDisableAppEngineDescription(
        serverGroupName: "does-not-exist",
        accountName: ACCOUNT_NAME,
        credentials: credentials
      )

      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      validator.appEngineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, "does-not-exist") >> null

      1 * errors.rejectValue("enableAppEngineAtomicOperationDescription.serverGroupName",
                             "enableAppEngineAtomicOperationDescription.serverGroupName.invalid " +
                             "(Server group does-not-exist not found).")
  }

  void "passes validation if server group found in cache"() {
    setup:
      def validator = new EnableAppEngineDescriptionValidator()
      validator.accountCredentialsProvider = accountCredentialsProvider
      validator.appEngineClusterProvider = Mock(AppEngineClusterProvider)

      def description = new EnableDisableAppEngineDescription(
        serverGroupName: SERVER_GROUP_NAME,
        accountName: ACCOUNT_NAME,
        credentials: credentials
      )

      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      validator.appEngineClusterProvider.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> SERVER_GROUP

      0 * errors._
  }
}
