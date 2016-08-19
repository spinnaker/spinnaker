package com.netflix.spinnaker.rosco.providers.util

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class LocalJobFriendlyPackerCommandFactorySpec extends Specification {

  @Shared
  LocalJobFriendlyPackerCommandFactory packerCommandFactory = new LocalJobFriendlyPackerCommandFactory()

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
}