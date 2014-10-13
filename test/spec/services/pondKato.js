/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* globals TasksFixture */

'use strict';

describe('Service: Pond - task complete, task force refresh', function() {

  var service, $http, config, scope, timeout, task;

  beforeEach(loadDeckWithoutCacheInitializer);

  beforeEach(inject(function(settings, pond, $httpBackend, $rootScope, $timeout) {

    service = pond;
    config = settings;
    $http = $httpBackend;
    timeout = $timeout;
    scope = $rootScope.$new();

    task = service.one('tasks', 1).get().$object;

  }));


  it('resolves watchForKatoTask immediately if kato task is present and complete', function() {

    $http.whenGET(config.pondUrl + '/tasks/1').respond(200, {
      id: 1,
      variables: [
        {
          key: 'kato.tasks',
          value: [TasksFixture.successfulKatoTask]
        }
      ],
      status: 'STARTED'
    });

    var result = null;

    $http.flush();

    task.getCompletedKatoTask().then(function(completedTask) {
      result = completedTask;
    });
    scope.$digest();

    expect(result.id).toBe(3);
    expect(result.history).toEqual(TasksFixture.successfulKatoTask.history);
  });

  it('rejects watchForKatoTask immediately if kato task is present and failed', function() {

    $http.whenGET(config.pondUrl + '/tasks/1').respond(200, {
      id: 1,
      variables: [
        {
          key: 'kato.tasks',
          value: [TasksFixture.failedKatoTask]
        }
      ],
      status: 'STARTED'
    });

    var result = null;

    $http.flush();

    task.getCompletedKatoTask().then(angular.noop, function(completedTask) {
      result = completedTask;
    });
    scope.$digest();

    expect(result.id).toBe(3);
    expect(result.history).toEqual(TasksFixture.failedKatoTask.history);
  });

  it('polls kato task until it completes, then resolves', function() {
    var result = null;

    $http.whenGET(config.pondUrl + '/tasks/1').respond(200, {
      id: 1,
      variables: [ { key: 'kato.last.task.id', value: { id: 3 } }],
      status: 'STARTED'
    });


    $http.flush();

    var katoRequestHandler = $http.whenGET(config.katoUrl + '/task/3');

    katoRequestHandler.respond(200, TasksFixture.runningKatoTask);

    task.getCompletedKatoTask().then(function(completedTask) {
      result = completedTask;
    });

    cycle();

    katoRequestHandler.respond(200, TasksFixture.successfulKatoTask);
    cycle();

    expect(result.id).toBe(3);
    expect(result.history).toEqual(TasksFixture.successfulKatoTask.history);
  });

  it('rejects immediately with null if pond task fails without kato task', function() {
    var result = 'not null';

    $http.whenGET(config.pondUrl + '/tasks/1').respond(200, {
      id: 1,
      variables: [],
      status: 'FAILED'
    });

    $http.flush();

    task.getCompletedKatoTask().then(angular.noop, function(completedTask) {
      result = completedTask;
    });

    scope.$digest();

    expect(result).toBe(null);

  });



  function cycle() {
    timeout.flush();
    $http.flush();
    scope.$digest();
  }

});
