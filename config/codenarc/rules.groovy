/*
 * Copyright 2015 Netflix, Inc.
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

String TEST_SOURCES = /.*\/src\/test\/groovy\/.*/

ruleset {

    description 'Codenarc Rules'

    ruleset('rulesets/basic.xml')
    ruleset('rulesets/braces.xml')
    ruleset('rulesets/concurrency.xml')
    ruleset('rulesets/convention.xml')
    ruleset('rulesets/design.xml')
    ruleset('rulesets/dry.xml') {
        'DuplicateStringLiteral' enabled: false
    }
    ruleset('rulesets/exceptions.xml')
    ruleset('rulesets/formatting.xml') {
        'ClassJavadoc' doNotApplyToFilesMatching: TEST_SOURCES
        'SpaceAroundMapEntryColon' enabled: false
        'BlankLineBeforePackage' enabled: false
    }

    ruleset('rulesets/generic.xml')
    ruleset('rulesets/groovyism.xml')
    ruleset('rulesets/imports.xml')
    ruleset('rulesets/jdbc.xml')
    ruleset('rulesets/junit.xml')
    ruleset('rulesets/logging.xml')
    ruleset('rulesets/naming.xml') {
        'MethodName' doNotApplyToFilesMatching: TEST_SOURCES
    }
    ruleset('rulesets/security.xml')
    ruleset('rulesets/size.xml') {
        'CrapMetric' enabled: false
    }
    ruleset('rulesets/unnecessary.xml')
    ruleset('rulesets/unused.xml')
}
