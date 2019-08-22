package com.netflix.spinnaker.echo

import com.netflix.spinnaker.echo.model.Event
import com.netflix.spinnaker.echo.telemetry.TelemetryEventListener
import com.netflix.spinnaker.echo.telemetry.TelemetryService
import spock.lang.Specification
import spock.lang.Subject

class TelemetryEventListenerSpec extends Specification {
    def service = Mock(TelemetryService)

    @Subject
    def listener = new TelemetryEventListener(service)

    void setup() {
        listener.instanceId = "test-instance"
    }

    def "send a telemetry event"() {
        given:
        Event event = new Event(
                details: [
                        type       : "orca:pipeline:complete",
                        application: "some-application"
                ],
                content: [
                        execution: [
                                id     : "execution_id",
                                type   : "PIPELINE",
                                status : "SUCCEEDED",
                                trigger: [
                                        type: "GIT"
                                ],
                                stages : [
                                        [
                                                type  : "deploy",
                                                status: "SUCCEEDED"
                                        ],
                                        [
                                                type  : "wait",
                                                status: "SUCCEEDED"
                                        ],
                                ]
                        ]
                ]
        )

        when:
        listener.processEvent(event)

        then:
        1 * service.sendMessage('{\n  "spinnakerInstance": {\n    "id": "test-instance",\n    "application": {\n      "id": "some-application",\n      "execution": {\n        "id": "execution_id",\n        "type": "PIPELINE",\n        "trigger": {\n          "type": "GIT"\n        },\n        "stages": [{\n          "type": "deploy",\n          "status": "SUCCEEDED"\n        }, {\n          "type": "wait",\n          "status": "SUCCEEDED"\n        }],\n        "status": "SUCCEEDED"\n      }\n    }\n  }\n}')
    }
}
