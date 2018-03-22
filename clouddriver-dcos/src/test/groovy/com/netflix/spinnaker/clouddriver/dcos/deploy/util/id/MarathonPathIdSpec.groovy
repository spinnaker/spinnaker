/*
 * Copyright 2018 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
