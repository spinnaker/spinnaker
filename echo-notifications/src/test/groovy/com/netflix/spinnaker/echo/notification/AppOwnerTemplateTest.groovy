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
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerProperties
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class AppOwnerTemplateTest extends Specification {
    @Shared
    def notificationTemplateEngine

    void setup() {
        def autoconfig = new FreeMarkerAutoConfiguration.FreeMarkerNonWebConfiguration()
        autoconfig.properties = new FreeMarkerProperties(preferFileSystemAccess: false)
        def config = autoconfig.freeMarkerConfiguration()
        config.afterPropertiesSet()
        notificationTemplateEngine = new NotificationTemplateEngine(
                configuration: config.object,
                spinnakerUrl: "SPINNAKER_URL"
        )
    }

    void "appOwnerMultipleUsers should handle fully formed notification"() {
        given:
        Notification not = buildFullNotification()
        not.templateGroup = templateGroup
        not.additionalContext.events = events

        when:
        def rendered = notificationTemplateEngine.build(not, NotificationTemplateEngine.Type.BODY).stripIndent()

        then:
        rendered == expected

        where:
        templateGroup = "appOwnerMultipleUsers"
        events = [
                [
                        email: "user1@host.net",
                        applications: [[name: "oneapp"]]
                ],[
                        email: "user2@host.net",
                        applications: [[name: "oneapp"], [name: "twoapp"]]
                ]

        ]
        expected = '''\
        <p>The owner email for the following applications matches multiple users:</p>
        user1@host.net owns...
        <ul>
          <li>oneapp</li>
        </ul>
        <br/>
        user2@host.net owns...
        <ul>
          <li>oneapp</li><li>twoapp</li>
        </ul>
        <br/>
        '''.stripIndent()
    }

    void "appOwnerOutdatedNoSuggestion should handle fully formed notification"() {
        given:
        Notification not = buildFullNotification()
        not.templateGroup = templateGroup
        not.additionalContext.events = events

        when:
        def rendered = notificationTemplateEngine.build(not, NotificationTemplateEngine.Type.BODY)

        then:
        rendered == expected

        where:
        templateGroup = "appOwnerOutdatedNoSuggestion"
        events = [
                [
                        user: [ name: "User 1", email: "user1@host.net"],
                        applications: [[name: "oneapp"]]
                ],[
                        user: [ name: "User 2", email: "user2@host.net"],
                        applications: [[name: "oneapp"], [name: "twoapp"]]
                ]

        ]
        expected = '''\
        <p>The following users are ex-employees but still own applications. We could not identify managers or team members to reassign to.</p>
        User 1 (user1@host.net) owns...
        <ul>
          <li>oneapp</li>
        </ul>
        <br/>
        User 2 (user2@host.net) owns...
        <ul>
          <li>oneapp</li><li>twoapp</li>
        </ul>
        <br/>
        '''.stripIndent()
    }

    void "appOwnerOutdatedReassign should handle fully formed notification"() {
        given:
        Notification not = buildFullNotification()
        not.templateGroup = templateGroup
        not.additionalContext.events = events

        when:
        def rendered = notificationTemplateEngine.build(not, NotificationTemplateEngine.Type.BODY)

        then:
        rendered == expected

        where:
        templateGroup = "appOwnerOutdatedReassign"
        events = [
                [
                        user: [ name: "User 1", email: "user1@host.net"],
                        applications: [[name: "oneapp"]],
                        suggestedUser: [name: "Mgr 1", email: "manager1@host.net"]
                ],[
                        user: [ name: "User 2", email: "user2@host.net"],
                        applications: [[name: "oneapp"], [name: "twoapp"]],
                        suggestedUser: [name: "Mgr 2", email: "manager2@host.net"]
                ]

        ]
        expected = '''\
        <p>The following users are ex-employees but still own applications. We have identified possible managers or team members to reassign to.</p>
        User 1 (user1@host.net) owns...
        <ul>
          <li>oneapp</li>
        </ul>
        <p>Suggest reassigning to Mgr 1 (manager1@host.net)</p>
        <br/>
        User 2 (user2@host.net) owns...
        <ul>
          <li>oneapp</li><li>twoapp</li>
        </ul>
        <p>Suggest reassigning to Mgr 2 (manager2@host.net)</p>
        <br/>
        '''.stripIndent()
    }

    void "appOwnerUnknown should handle fully formed notification"() {
        given:
        Notification not = buildFullNotification()
        not.templateGroup = templateGroup
        not.additionalContext.events = events

        when:
        def rendered = notificationTemplateEngine.build(not, NotificationTemplateEngine.Type.BODY)

        then:
        rendered == expected

        where:
        templateGroup = 'appOwnerUnknown'
        events = [
                [
                        email: "user1@host.net",
                        applications: [[name: "oneapp"]]
                ],[
                        email: "user2@host.net",
                        applications: [[name: "oneapp"], [name: "twoapp"]]
                ]

        ]
        expected = '''\
        <p>The following email addresses are not recognized employee or group addresses but own applications.</p>
        user1@host.net owns...
        <ul>
          <li>oneapp</li>
        </ul>
        <br/>
        user2@host.net owns...
        <ul>
          <li>oneapp</li><li>twoapp</li>
        </ul>
        <br/>
        '''.stripIndent()
    }

    void "appsReassigned should handle fully formed notification"() {
        given:
        Notification not = buildFullNotification()
        not.templateGroup = templateGroup
        not.additionalContext.events = events

        when:
        def rendered = notificationTemplateEngine.build(not, NotificationTemplateEngine.Type.BODY)

        then:
        rendered == expected

        where:
        templateGroup = "appsReassigned"
        events = [
                [
                        user: [ name: "User 1", email: "user1@host.net"],
                        applications: [[name: "oneapp"]]
                ],[
                        user: [ name: "User 2", email: "user2@host.net"],
                        applications: [[name: "oneapp"], [name: "twoapp"]]
                ]

        ]
        expected = '''\
        <p>The following applications have been reassigned to you as their owner is no longer a Netflix employee and you are the current manager of their team.</p>
        User 1 (user1@host.net) owned...
        <ul>
          <li>oneapp</li>
        </ul>
        <br/>
        User 2 (user2@host.net) owned...
        <ul>
          <li>oneapp</li><li>twoapp</li>
        </ul>
        <br/>
        '''.stripIndent()
    }
    Notification buildFullNotification() {
        Notification notification = new Notification()
        notification.templateGroup = "TODO-fill-in-specific-test"
        notification.notificationType = Notification.Type.EMAIL
        notification.source = new Notification.Source()
        notification.severity = Notification.Severity.NORMAL
        notification.to = ["user@host.net"]
        notification.cc = []
        notification.additionalContext = [:]


        return notification
    }

}
