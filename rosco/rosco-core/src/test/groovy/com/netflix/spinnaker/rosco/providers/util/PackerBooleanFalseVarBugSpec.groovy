package com.netflix.spinnaker.rosco.providers.util

import com.netflix.spinnaker.rosco.config.RoscoPackerConfigurationProperties
import spock.lang.Specification

/**
 * Proves that LocalJobFriendlyPackerCommandFactory silently drops packer -var entries whose
 * value is a Boolean false (or numeric 0), even though provider bake handlers deliberately place
 * such values into the parameter map (guarded by != null) intending them to override packer
 * template defaults.
 *
 * Concrete reachable case: AWSBakeHandler sets
 *   parameterMap.aws_associate_public_ip_address = awsBakeryDefaults.awsAssociatePublicIpAddress
 * inside a `!= null` guard, and the aws-ebs.json template default is "true". When an operator
 * configures awsAssociatePublicIpAddress: false, the -var is dropped and packer uses the template
 * default (true) -- the OPPOSITE of what was configured.
 */
class PackerBooleanFalseVarBugSpec extends Specification {

  LocalJobFriendlyPackerCommandFactory packerCommandFactory = new LocalJobFriendlyPackerCommandFactory(
    roscoPackerConfigurationProperties: new RoscoPackerConfigurationProperties()
  )

  void "a Boolean false parameter value must be passed through as -var key=false"() {
    setup:
      def parameterMap = [
        aws_associate_public_ip_address: Boolean.FALSE
      ]

    when:
      def packerCommand = packerCommandFactory.buildPackerCommand("", parameterMap, null, "")

    then:
      packerCommand == ["packer", "build", "-color=false", "-var", "aws_associate_public_ip_address=false"]
  }

  void "a numeric 0 parameter value must be passed through as -var key=0"() {
    setup:
      def parameterMap = [
        some_count: 0
      ]

    when:
      def packerCommand = packerCommandFactory.buildPackerCommand("", parameterMap, null, "")

    then:
      packerCommand == ["packer", "build", "-color=false", "-var", "some_count=0"]
  }
}
