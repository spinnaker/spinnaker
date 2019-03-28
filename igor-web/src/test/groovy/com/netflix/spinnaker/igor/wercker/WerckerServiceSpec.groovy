/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.igor.wercker

import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.igor.config.WerckerProperties.WerckerHost
import com.netflix.spinnaker.igor.wercker.model.*

import spock.lang.Shared
import spock.lang.Specification

class WerckerServiceSpec extends Specification {

    WerckerCache cache = Mock(WerckerCache)

    @Shared
    WerckerClient client

    @Shared
    WerckerService service
    String werckerDev = 'https://dev.wercker.com/'
    String master = 'WerckerTestMaster'

    void setup() {
        client = Mock(WerckerClient)
        service = new WerckerService(
                new WerckerHost(name: master, address: werckerDev), cache, client, Permissions.EMPTY)
    }

    void 'get pipelines as jobs'() {
        setup:
        client.getApplications(_,_) >> [
            appOf('myApp1', 'coo', [pipeOf('myPipeA', 'newType')]),
            appOf('myApp2', 'foo', [
                pipeOf('myPipeX', 'x'),
                pipeOf('myPipeY', 'y')
            ])
        ]

        expect:
        service.jobs.size == 3
        service.jobs.contains('x/foo/myApp2/myPipeX')
    }

    void 'get pipelineId and builds'() {
        setup:
        def names = ['myOrg', 'myApp', 'myPipe']
        def (org, app, pipe) = names
        def pipeline = names.join('/')
        def pipelineId = pipe + "ID"
        List<Pipeline> pipelines = [
            pipeOf('anotherPipe', 'git'),
            pipeOf(pipe, 'git', pipelineId)
        ]
        def now = System.currentTimeMillis()
        List<Run> runs = [
            runOf('1', now-1, appOf(app, org, []), pipelines[1]),
            runOf('2', now-2, appOf(app, org, []), pipelines[1]),
            runOf('3', now-3, appOf(app, org, []), pipelines[1])
        ]
        client.getPipelinesForApplication(_, org, app) >> pipelines
        client.getRunsForPipeline(_, pipelineId) >> runs

        expect:
        service.getBuilds(pipeline) == runs
    }

    void 'get builds with pipelineId'() {
        setup:
        def (org, app, pipe) = ["myOrg", "myApp", "myPipe"]
        def (pipelineId, pipeline) = [
            'myPipelineID',
            org + '/' + app + '/' + pipe
        ]
        def now = System.currentTimeMillis()
        List<Run> runs = [
            runOf('1', now-1, null, null),
            runOf('2', now-2, appOf(app, org, []), pipeOf(pipe, 'git')),
            runOf('3', now-3, appOf(app, org, []), pipeOf(pipe, 'git'))
        ]
        cache.getPipelineID(_, pipeline) >> pipelineId
        client.getRunsForPipeline(_, pipelineId) >> runs

        expect:
        service.getBuilds(pipeline) == runs
    }

    void 'categorize runs with pipelineQName'() {
        setup:
        long now = System.currentTimeMillis()
        long since = now-1000
        def app1 = appOf('app1', 'org1', [])
        def app2 = appOf('app2', 'org1', [])
        def pipe1 = pipeOf('p1', 'git', 'a', app1)
        def pipe2 = pipeOf('p2', 'git', 'b', app2)
        def pipe3 = pipeOf('p3', 'git', 'c', app1)
        List<Run> runs1 = [
            runOf('1', now-10, app1, pipe1, 'a'),
            runOf('2', now-10, app2, pipe2, 'b'),
            runOf('3', now-10, app1, pipe3, 'c'),
            runOf('4', now-10, app2, pipe2, 'b'),
        ]
        client.getRunsSince(_,_,_,_,since) >> runs1
        client.getPipeline(_, 'a') >> pipe1
        client.getPipeline(_, 'b') >> pipe2
        client.getPipeline(_, 'c') >> pipe3

        expect:
        service.getRunsSince(since).size() == 3
        service.getRunsSince(since).get('org1/app2/p2').size() == 2
        service.getRunsSince(since).get('org1/app1/p1').size() == 1
        service.getRunsSince(since).get('org1/app1/p3').size() == 1
    }

    void 'get GenericBuild with buildNumber'() {
        def names = [
            'testOrg',
            'testApp',
            'testPipe'
        ]
        def (org, app, pipe) = names
        def job = names.join('/')
        def runId = "testGenericBuild_werckerRunId"
        int buildNumber = 6
        setup:
        cache.getRunID(master, job, buildNumber) >> runId
        client.getRunById(_, runId) >> new Run(id: runId)

        expect:
        service.getGenericBuild(job, buildNumber).url == werckerDev + org + '/' + app + '/runs/' + pipe + '/' + runId
        service.getGenericBuild(job, buildNumber).building == true
    }

    void 'test triggerBuildWithParameters'() {
        def names = [
            'testOrg',
            'testApp',
            'testPipe'
        ]
        def (org, app, pipe) = names
        def (pipeline, pipelineId) = [names.join('/'), pipe + "ID"]
        def runId = 'test_triggerBuild_runId'
        int buildNumber = 8

        setup:
        client.getPipelinesForApplication(_, org, app) >> [
            pipeOf('foo', 'git'),
            pipeOf(pipe, 'git', pipelineId)
        ]
        client.triggerBuild(_, _) >> ['id': runId]
        client.getRunById(_, runId) >> new Run(id: runId)
        cache.updateBuildNumbers(master, pipeline, _) >> ['test_triggerBuild_runId': buildNumber]

        expect:
        service.triggerBuildWithParameters(pipeline, [:]) == buildNumber
    }

    Application appOf(String name, String owner, List<Pipeline> pipelines) {
        return new Application(name: name, owner: new Owner(name: owner), pipelines: pipelines)
    }

    Pipeline pipeOf(String name, String type, String id=name) {
        return new Pipeline(id: id, name: name, type: type)
    }

    Pipeline pipeOf(String name, String type, String id=name, Application app) {
        return new Pipeline(id: id, name: name, type: type, application: app)
    }

    Run runOf(String id, long startedAt, Application app, Pipeline pipe, String pid=null) {
        return new Run(id: id, startedAt: new Date(startedAt), application: app, pipeline: pipe, pipelineId: pid)
    }
}
