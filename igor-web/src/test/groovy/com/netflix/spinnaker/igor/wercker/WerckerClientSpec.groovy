
/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.wercker

import com.netflix.spinnaker.config.okhttp3.InsecureOkHttpClientBuilderProvider
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.igor.config.*
import com.netflix.spinnaker.igor.config.WerckerProperties.WerckerHost
import com.netflix.spinnaker.igor.wercker.model.*
import com.squareup.okhttp.mockwebserver.MockResponse
import com.squareup.okhttp.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import spock.lang.Shared
import spock.lang.Specification

class WerckerClientSpec extends Specification {

    @Shared
    WerckerClient client

    @Shared
    MockWebServer server

    void setup() {
        server = new MockWebServer()
    }

    void cleanup() {
        server.shutdown()
    }

    void 'get all applications'() {
        setup:
        def authHeader = "my_authHeader"
        def limit = 300
        setResponse 'getApplications.js'

        when:
        List<Application> apps = client.getApplications(authHeader, limit)

        then:
        def request = server.takeRequest()

        expect:
        apps.size() == 5
        request.path.startsWith('/api/spinnaker/v1/applications?')
        request.path.contains('limit=' + limit)
        assertApp(apps[0])
        assertApp(apps[2])
    }

    void 'get all runs since time'() {
        given:
        def authHeader = "my_authHeader"
        def time = System.currentTimeMillis()
        def branch = 'master'
        def limit = 300
        setResponse 'getRuns.js'

        when:
        List<Run> runs = client.getRunsSince(authHeader, branch, ['x', 'y', 'z'], limit, time)

        then:
        def request = server.takeRequest()

        expect:
        request.path.startsWith('/api/spinnaker/v1/runs?')
        request.path.contains('branch=' + branch)
        request.path.contains( 'limit=' + limit)
        request.path.contains( 'since=' + time)
        request.path.contains( 'pipelineIds=x')
        request.path.contains( 'pipelineIds=y')
        request.path.contains( 'pipelineIds=z')
        runs.size() == 5
        assertRun(runs[1])
        assertRun(runs[4])
    }

    def assertApp(Application app) {
        app.id && app.name && app.owner && app.pipelines
    }

    def assertRun(Run run) {
        run.id && run.status && run.user &&
                run.application && run.pipeline
    }

    private void setResponse(String fileName) {
        server.enqueue(
                new MockResponse()
                .setBody(read(fileName))
                .setHeader('Content-Type', 'application/json; charset=utf-8')
                )
        server.start()
        def host = new WerckerHost(name: 'werckerMaster', address: server.url('/').toString())
        client = new WerckerConfig().werckerClient(host, 30000, new OkHttpClientProvider([new InsecureOkHttpClientBuilderProvider(new OkHttpClient())]))
    }

    String read(String fileName) {
        return new File(getClass().getResource('/wercker/' + fileName).toURI()).text
    }
}
