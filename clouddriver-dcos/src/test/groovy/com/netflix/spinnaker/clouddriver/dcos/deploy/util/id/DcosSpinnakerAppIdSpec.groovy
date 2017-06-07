package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import com.netflix.frigga.Names
import spock.lang.Specification
import spock.lang.Unroll

class DcosSpinnakerAppIdSpec extends Specification {
  static final def ACCOUNT = "spinnaker"
  static final def INVALID_MARATHON_PART = "-iNv.aLiD-"

  @Unroll
  void "static factory method should return an empty optional for invalid path \"#path\""() {
    expect:
    !DcosSpinnakerAppId.parseVerbose(path).present

    where:
    path << [
      null,
      "",
      "      ",
      "$INVALID_MARATHON_PART",
      "/",
      "/       ",
      "/$INVALID_MARATHON_PART",
      "spinnaker",
      "spinnaker/",
      "spinnaker/         ",
      "spinnaker/$INVALID_MARATHON_PART",
      "/spinnaker",
      "/spinnaker/",
      "/spinnaker/         ",
      "/spinnaker/$INVALID_MARATHON_PART",
      "spinnaker//service-v000",
      "spinnaker/         /service-v000",
      "spinnaker/$INVALID_MARATHON_PART/service-v000",
      "spinnaker/test//service-v000",
      "spinnaker/test/         /service-v000",
      "spinnaker/test/$INVALID_MARATHON_PART/service-v000",
      "spinnaker/test_/service-v000",
      "spinnaker/test_         /service-v000",
      "spinnaker/test_$INVALID_MARATHON_PART/service-v000",
      "/spinnaker//service-v000",
      "/spinnaker/         /service-v000",
      "/spinnaker/$INVALID_MARATHON_PART/service-v000",
      "/spinnaker/test//service-v000",
      "/spinnaker/test/         /service-v000",
      "/spinnaker/test/$INVALID_MARATHON_PART/service-v000",
      "/spinnaker/test_/service-v000",
      "/spinnaker/test_         /service-v000",
      "/spinnaker/test_$INVALID_MARATHON_PART/service-v000"
    ]

  }

  @Unroll
  void "static factory method should return an empty optional if the given invalid account, group, and/or serverGroupName: [#account, #group, #serverGroupName]"() {
    expect:
    !DcosSpinnakerAppId.fromVerbose(account, group, serverGroupName).present

    where:
    account                  | group                    | serverGroupName
    null                     | "test/service"           | "service-v000"
    ""                       | "test/service"           | "service-v000"
    "     "                  | "test/service"           | "service-v000"
    "$INVALID_MARATHON_PART" | "test/service"           | "service-v000"
    ACCOUNT                  | "    "                   | "service-v000"
    ACCOUNT                  | "$INVALID_MARATHON_PART" | "service-v000"
    ACCOUNT                  | "test/service"           | null
    ACCOUNT                  | "test/service"           | ""
    ACCOUNT                  | "test/service"           | "         "
    ACCOUNT                  | "test/service"           | "$INVALID_MARATHON_PART"
  }

  @Unroll
  void "the account (#expectedAccount), group (#expectedSafeGroup), unsafe group (#expectedUnsafeGroup) and service (#expectedService) should be correctly parsed when given a valid path \"#path\""() {
    expect:
    def dcosPath = DcosSpinnakerAppId.parseVerbose(path).get()
    dcosPath.account == expectedAccount
    dcosPath.safeGroup == expectedSafeGroup
    dcosPath.unsafeGroup == expectedUnsafeGroup
    dcosPath.serverGroupName == Names.parseName(expectedService)

    where:
    path                                  || expectedAccount || expectedSafeGroup || expectedUnsafeGroup || expectedService
    "spinnaker/test/service-v000"         || "spinnaker"     || "test"            || "test"              || "service-v000"
    "spinnaker/test/service/service-v000" || "spinnaker"     || "test_service"    || "test/service"      || "service-v000"
  }

  @Unroll
  void "the account (#expectedAccount), group (#expectedSafeGroup), unsafe group (#expectedUnsafeGroup) and service (#expectedService) should be correctly parsed when given a valid absolute path \"#path\""() {
    expect:
    def dcosPath = DcosSpinnakerAppId.parseVerbose(path).get()
    dcosPath.account == expectedAccount
    dcosPath.safeGroup == expectedSafeGroup
    dcosPath.unsafeGroup == expectedUnsafeGroup
    dcosPath.serverGroupName == Names.parseName(expectedService)

    where:
    path                                   || expectedAccount || expectedSafeGroup || expectedUnsafeGroup || expectedService
    "/spinnaker/test/service-v000"         || "spinnaker"     || "test"            || "test"              || "service-v000"
    "/spinnaker/test/service/service-v000" || "spinnaker"     || "test_service"    || "test/service"      || "service-v000"
  }

  @Unroll
  void "the namespace (#expectedNamespace) and full path (#expectedFullPath) should be correctly built when given a valid account (#account), group (#group), serverGroup (#serverGroupName)"() {
    expect:
    def dcosPath = DcosSpinnakerAppId.fromVerbose(account, group, serverGroupName).get()
    dcosPath.toString() == expectedFullPath

    where:
    account     | group          | serverGroupName || expectedNamespace         || expectedFullPath
    "spinnaker" | "test"         | "service-v000"  || "/spinnaker/test"         || "/spinnaker/test/service-v000"
    "spinnaker" | "test/service" | "service-v000"  || "/spinnaker/test/service" || "/spinnaker/test/service/service-v000"
    "spinnaker" | "test_service" | "service-v000"  || "/spinnaker/test/service" || "/spinnaker/test/service/service-v000"
  }

  @Unroll
  void "given a valid marathon path \"#path\" the parsed path should be \"#expectedFullPath\""() {
    expect:
    def dcosPath = DcosSpinnakerAppId.parseVerbose(path).get()
    dcosPath.toString() == expectedFullPath

    where:
    path                                  || expectedFullPath
    "spinnaker/test/service-v000"         || "/spinnaker/test/service-v000"
    "spinnaker/test/service/service-v000" || "/spinnaker/test/service/service-v000"
  }

  @Unroll
  void "given a valid absolute marathon path \"#path\" the parsed path should be \"#expectedFullPath\""() {
    expect:
    def dcosPath = DcosSpinnakerAppId.parseVerbose(path).get()
    dcosPath.toString() == expectedFullPath

    where:
    path                                   || expectedFullPath
    "/spinnaker/test/service-v000"         || "/spinnaker/test/service-v000"
    "/spinnaker/test/service/service-v000" || "/spinnaker/test/service/service-v000"
  }
}
