/*
 * Copyright 2020 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.echo.util

import spock.lang.Specification

class ManualJudgmentAuthzGroupsUtilSpec extends Specification {


  void 'should return the result based on userRoles, stageRoles and permissions'() {

    when:
    def result = ManualJudgmentAuthzGroupsUtil.checkAuthorizedGroups(userRoles,stageRoles,permissions)

    then:
    result == output

    where:
    userRoles           |    stageRoles       |     permissions                                                       |    output
    ['foo','baz']       |   ['foo']           |    ["WRITE": ["foo"],"READ": ["foo","baz"], "EXECUTE": ["foo"]]       |    true
    ['foo','baz']       |   []                |    ["WRITE": ["foo"],"READ": ["foo","baz"], "EXECUTE": ["foo"]]       |    true
    []                  |   ['foo']           |    ["WRITE": ["foo"],"READ": ["foo","baz"], "EXECUTE": ["foo"]]       |    false
    ['foo','baz']       |   ['baz']           |    ["WRITE": ["foo"],"READ": ["foo","baz"], "EXECUTE": ["foo"]]       |    false
    ['foo','baz']       |   []                |    ["":""]                                                            |    true
    ['foo','baz','bar'] |   ['baz']           |    ["WRITE": ["bar"],"READ": ["bar"], "EXECUTE": ["bar"]]             |    false
  }
}
