package com.netflix.spinnaker.rosco.providers.util

import spock.lang.Shared
import spock.lang.Specification

class LocalJobFriendlyPackerCommandFactorySpec extends Specification {

  @Shared
  LocalJobFriendlyPackerCommandFactory packerCommandFactory = new LocalJobFriendlyPackerCommandFactory()

  void "packerCommand handles baseCommand as null, empty string and real string"() {
    setup:
      def parameterMap = [
        something:   something
      ]

    when:
      def packerCommand = packerCommandFactory.buildPackerCommand(baseCommand, parameterMap, "")

    then:
      packerCommand == expectedPackerCommand

    where:
      something | baseCommand | expectedPackerCommand
      "sudo"    | "sudo"      | ["sudo", "packer", "build", "-color=false", "-var", "something=sudo"]
      "null"    | null        | ["packer", "build", "-color=false", "-var", "something=null"]
      "empty"   | ""          | ["packer", "build", "-color=false", "-var", "something=empty"]
  }
}