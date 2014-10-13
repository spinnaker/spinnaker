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

'use strict';

describe('Service: Pond - task complete, task force refresh', function() {

  var service, $http, config, scope, timeout, task;

  beforeEach(module('deckApp'));

  beforeEach(inject(function(settings, pond, $httpBackend, $rootScope, $timeout) {

    service = pond;
    config = settings;
    $http = $httpBackend;
    timeout = $timeout;
    scope = $rootScope.$new();

    task = service.one('tasks', 1).get().$object;

  }));


  it('resolves watchForTaskComplete immediately if task is complete', function() {

    $http.whenGET(config.pondUrl + '/tasks/1').respond(200, {
      id: 1,
      status: 'COMPLETED'
    });

    var result = null;

    $http.flush();

    task.watchForTaskComplete().then(function(completedTask) {
      result = completedTask;
    });
    scope.$digest();

    expect(result.id).toBe(1);
    expect(result.status).toBe('COMPLETED');
  });


  it('polls task when watchForTaskComplete is called until task completes', function() {

    var result = null,
        requestHandler = $http.whenGET(config.pondUrl + '/tasks/1');

    // Step 1: running
    requestHandler.respond(200, { id: 1, status: 'STARTED' });
    $http.flush();

    task.watchForTaskComplete().then(function(completedTask) { result = completedTask; });

    scope.$digest();

    expect(result).toBe(null);

    // Step 2: still running
    requestHandler.respond(200, { id: 1, status: 'STARTED' });
    cycle();
    expect(result).toBe(null);

    // Step 3: succeeded
    requestHandler.respond(200, { id: 1, status: 'COMPLETED' });
    cycle();
    expect(result.status).toBe('COMPLETED');
  });


  it('polls task, failing when task fails or stops', function() {

    var result = null,
      requestHandler = $http.whenGET(config.pondUrl + '/tasks/1');

    // Scenario 1: FAILED
    // Step 1: running
    requestHandler.respond(200, { id: 1, status: 'STARTED' });
    $http.flush();

    task.watchForTaskComplete().then(angular.noop, function(failedTask) { result = failedTask; });

    scope.$digest();

    expect(result).toBe(null);

    // Step 2: FAILED
    requestHandler.respond(200, { id: 1, status: 'FAILED' });
    cycle();
    expect(result.id).toBe(1);
    expect(result.status).toBe('FAILED');

    // Scenario 2: STOPPED
    // Step 1: running
    requestHandler.respond(200, { id: 1, status: 'STARTED' });
    task.get().then(function() {
      task.watchForTaskComplete().then(angular.noop, function(failedTask) { result = failedTask; });
    });

    $http.flush();
    scope.$digest();

    // Step 2: STOPPED
    requestHandler.respond(200, { id: 1, status: 'STOPPED' });
    cycle();
    expect(result.id).toBe(1);
    expect(result.status).toBe('STOPPED');
  });


  it('waits for force refresh step to complete, then resolves', function() {
    var result = null,
      requestHandler = $http.whenGET(config.pondUrl + '/tasks/1');

    requestHandler.respond(200, { id: 1, status: 'STARTED', steps: [] });
    $http.flush();

    task.watchForForceRefresh().then(function(updatedTask) { result = updatedTask; });

    // Step 1: No step found (retry)
    scope.$digest();
    expect(result).toBe(null);

    // Step 2: Step found, running (retry)
    requestHandler.respond(200, {
      id: 1,
      status: 'STARTED',
      steps: [{
        name: 'ForceCacheRefreshStep',
        status: 'STARTED'
      }]
    });
    cycle();
    expect(result).toBe(null);

    // Step 3: Step found, completed
    requestHandler.respond(200, {
      id: 1,
      status: 'STARTED',
      steps: [{
        name: 'ForceCacheRefreshStep',
        status: 'COMPLETED'
      }]
    });

    cycle();
    expect(result.status).toBe('STARTED');

  });

  it('rejects when force refresh step fails', function() {
    var result = null,
      requestHandler = $http.whenGET(config.pondUrl + '/tasks/1');

    requestHandler.respond(200, { id: 1, status: 'STARTED', steps: [] });
    $http.flush();

    task.watchForForceRefresh().then(angular.noop, function(updatedTask) { result = updatedTask; });

    // Step 1: No step found (retry)
    scope.$digest();
    expect(result).toBe(null);

    // Step 2: Step found, running (retry)
    requestHandler.respond(200, {
      id: 1,
      status: 'STARTED',
      steps: [{
        name: 'ForceCacheRefreshStep',
        status: 'STARTED'
      }]
    });
    cycle();
    expect(result).toBe(null);

    // Step 3: Step found, completed
    requestHandler.respond(200, {
      id: 1,
      status: 'STARTED',
      steps: [{
        name: 'ForceCacheRefreshStep',
        status: 'FAILED'
      }]
    });

    cycle();
    expect(result.status).toBe('STARTED');
  });

  it('rejects when force refresh step never occurs', function() {
    var result = null,
      requestHandler = $http.whenGET(config.pondUrl + '/tasks/1');

    requestHandler.respond(200, { id: 1, status: 'COMPLETED', steps: [] });
    $http.flush();

    task.watchForForceRefresh().then(angular.noop, function(updatedTask) { result = updatedTask; });

    // Step 1: No step found (retry)
    scope.$digest();
    expect(result.status).toBe('COMPLETED');
  });


  function cycle() {
    timeout.flush();
    $http.flush();
    scope.$digest();
  }

});
