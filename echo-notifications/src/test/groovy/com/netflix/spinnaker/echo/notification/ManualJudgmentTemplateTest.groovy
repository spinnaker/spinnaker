/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.echo.notification

import com.netflix.spinnaker.echo.api.Notification
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerNonWebConfiguration
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerProperties
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ManualJudgmentTemplateTest extends Specification {
    @Shared
    def notificationTemplateEngine

    void setup() {
        def properties = new FreeMarkerProperties(preferFileSystemAccess: false)
        def autoconfig = new FreeMarkerNonWebConfiguration(properties)
        def config = autoconfig.freeMarkerConfiguration()
        config.afterPropertiesSet()
        notificationTemplateEngine = new NotificationTemplateEngine(
                configuration: config.object,
                spinnakerUrl: "SPINNAKER_URL"
        )
    }

    void "should handle fully formed notification"() {
        given:
        Notification not = buildFullNotification()

        when:
        def rendered = notificationTemplateEngine.build(not, NotificationTemplateEngine.Type.BODY)

        then:
        rendered == expected

        where:
        expected = '''\
        Stage stage-name for testapp's exe-name pipeline build 12345 is awaiting manual judgment.

        *Instructions:*
        Do the thing <http://foo>

        For more details, please visit:
        SPINNAKER_URL/#/applications/testapp/executions/details/exec-id?refId=stage-id&step=1
        '''.stripIndent()
    }

    @Unroll
    void "should handle #description in notification"() {
        given:
        Notification not = buildFullNotification()
        not.additionalContext.execution.trigger = trigger

        when:
        def rendered = notificationTemplateEngine.build(not, NotificationTemplateEngine.Type.BODY)

        then:
        rendered == expected

        where:
        expected = '''\
        Stage stage-name for testapp's exe-name pipeline is awaiting manual judgment.

        *Instructions:*
        Do the thing <http://foo>

        For more details, please visit:
        SPINNAKER_URL/#/applications/testapp/executions/details/exec-id?refId=stage-id&step=1
        '''.stripIndent()


        trigger           | description
        null              | "null trigger"
        [:]               | "empty trigger"
        [buildInfo: null] | "null buildInfo"
        [buildInfo: [:]]  | "empty buildInfo"
    }

    Notification buildFullNotification() {
        Notification notification = new Notification()
        notification.templateGroup = "manualJudgment"
        notification.notificationType = Notification.Type.SLACK
        notification.source = new Notification.Source()
        notification.source.application = "testapp"
        notification.source.executionId = "exec-id"
        notification.source.executionType = "pipeline"
        notification.source.user = "testuser"
        notification.severity = Notification.Severity.NORMAL
        notification.to = ["#channelname"]
        notification.cc = []
        notification.additionalContext = [
                execution: [
                        name: "exe-name",
                        trigger: [
                                buildInfo: [
                                        number: 12345
                                ]
                        ]
                ],
                "stageId": "stage-id",
                "stageName": "stage-name",
                "instructions": 'Do the <a href="http://foo">thing</a>',
                "restrictExecutionDuringTimeWindow": true
        ]


        return notification
    }

}
