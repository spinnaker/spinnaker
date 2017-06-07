package com.netflix.spinnaker.clouddriver.dcos.deploy.util.id

import spock.lang.Specification
import spock.lang.Unroll

class MarathonPathIdSpec extends Specification {
  static final def ACCOUNT = "spinnaker"

  @Unroll
  void "constructor should throw an IllegalArgumentException for invalid path \"#path\""() {
    when:
    MarathonPathId.parse(path)

    then:
    thrown(IllegalArgumentException)

    where:
    path << [
      null,
      "",
      "/",
      "    ",
      "/    ",
      ".",
      "/.",
      "..",
      "/..",
      "-",
      "/-",
      "--",
      "/--",
      ".app",
      "/.app",
      "-app",
      "/-app",
      "app.",
      "/app.",
      "app-",
      "/app-",
      "ap.p",
      "/ap.p",
      "ap..p",
      "/ap..p"
    ]
  }

  @Unroll
  void "constructor should parse valid path \"#path\""() {
    when:
    MarathonPathId.parse(path)

    then:
    notThrown(IllegalArgumentException)

    where:
    path << [
      "ap--p",
      "/ap--p",
      "app",
      "/app",
      "ap-p",
      "/ap-p"
    ]
  }

  @Unroll
  void "constructor should throw an IllegalArgumentException for invalid parts #parts"() {
    when:
    MarathonPathId.from(parts)

    then:
    thrown(IllegalArgumentException)

    where:
    parts << [
      null,
      ["", "app"] as String[],
      ["/", "app"] as String[],
      ["   ", "app"] as String[],
      ["/   ", "app"] as String[],
      [".", "app"] as String[],
      ["/.", "app"] as String[],
      ["..", "app"] as String[],
      ["/..", "app"] as String[],
      ["-", "app"] as String[],
      ["/-", "app"] as String[],
      ["--", "app"] as String[],
      ["/--", "app"] as String[],
      [".app", "app"] as String[],
      ["/.app", "app"] as String[],
      ["-app", "app"] as String[],
      ["/-app", "app"] as String[],
      ["app.", "app"] as String[],
      ["/app.", "app"] as String[],
      ["app-", "app"] as String[],
      ["/app-", "app"] as String[],
      ["ap.p", "app"] as String[],
      ["/ap.p", "app"] as String[],
      ["/ap-p", "app"] as String[],
      ["ap..p", "app"] as String[],
      ["/ap..p", "app"] as String[],
      ["/ap--p", "app"] as String[],
      ["/app", "app"] as String[]
    ]
  }

  @Unroll
  void "constructor should not throw an IllegalArgumentException for valid parts #parts"() {
    when:
    MarathonPathId.from(parts)

    then:
    notThrown(IllegalArgumentException)

    where:
    parts << [
      ["app", "app"] as String[],
      ["ap--p", "app"] as String[],
      ["ap-p", "app"] as String[]
    ]
  }
}
