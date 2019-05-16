package com.netflix.spinnaker.rosco.providers.util

import com.netflix.spinnaker.rosco.config.RoscoPackerConfigurationProperties
import com.netflix.spinnaker.rosco.jobs.JobRequest
import org.apache.commons.exec.CommandLine
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class LocalJobFriendlyPackerCommandFactorySpec extends Specification implements TestDefaults {

  @Shared
  LocalJobFriendlyPackerCommandFactory packerCommandFactory = new LocalJobFriendlyPackerCommandFactory(
    roscoPackerConfigurationProperties: new RoscoPackerConfigurationProperties()
  )

  @Unroll
  void "packerCommand handles baseCommand as null, empty string and real string"() {
    setup:
      def parameterMap = [
        something: something
      ]

    when:
      def packerCommand = packerCommandFactory.buildPackerCommand(baseCommand, parameterMap, null, "")

    then:
      packerCommand == expectedPackerCommand

    where:
      something | baseCommand | expectedPackerCommand
      "sudo"    | "sudo"      | ["sudo", "packer", "build", "-color=false", "-var", "something=sudo"]
      "null"    | null        | ["packer", "build", "-color=false", "-var", "something=null"]
      "empty"   | ""          | ["packer", "build", "-color=false", "-var", "something=empty"]
  }

  @Unroll
  void "packerCommand includes -varFileName only when 'varFile' is specified; varFile is #varFile"() {
    setup:
      def parameterMap = [
        something: "some-var"
      ]

    when:
      def packerCommand = packerCommandFactory.buildPackerCommand("", parameterMap, varFile, "")

    then:
      packerCommand == expectedPackerCommand

    where:
      varFile            | expectedPackerCommand
      null               | ["packer", "build", "-color=false", "-var", "something=some-var"]
      ""                 | ["packer", "build", "-color=false", "-var", "something=some-var"]
      "someVarFile.json" | ["packer", "build", "-color=false", "-var", "something=some-var", "-var-file=someVarFile.json"]
  }

  @Unroll
  void "packerCommand includes parameter with non-quoted string"() {

    when:
    def packerCommand = packerCommandFactory.buildPackerCommand("", parameterMap, null, "")

    then:
    packerCommand == expectedPackerCommand

    where:
    parameterMap                      | expectedPackerCommand
    [packages: "package1 package2"]   | ["packer", "build", "-color=false", "-var", "packages=package1 package2"]
  }

  @Unroll
  void 'validate packer command line' () {
    setup:

    when:
      def packerCommand = packerCommandFactory.buildPackerCommand("", parameterMap, null, "")
      def jobRequest = new JobRequest(tokenizedCommand: packerCommand, maskedParameters: maskedPackerParameters, jobId: SOME_UUID)
      def commandLine = new CommandLine(jobRequest.tokenizedCommand[0])
      def arguments = (String []) Arrays.copyOfRange(jobRequest.tokenizedCommand.toArray(), 1, jobRequest.tokenizedCommand.size())
      commandLine.addArguments(arguments, false)
      def g = commandLine.toString()
      def cmdLineList =  commandLine.toStrings().toList()


    then:
      cmdLineList  == expectedCommandLine

    where:
      parameterMap                          | maskedPackerParameters | expectedCommandLine
      [packages: "package1 package2"]       | []                     | ["packer", "build", "-color=false", "-var", "packages=package1 package2"]
  }
}
