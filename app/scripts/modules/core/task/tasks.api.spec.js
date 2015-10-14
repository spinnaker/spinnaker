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

describe('Service: tasksApi - task complete, task force refresh', function() {
  const angular = require('angular');

  var service, $http, scope, timeout, task;

  beforeEach(
    window.module(
      require('./tasks.api.js')
    )
  );


  beforeEach(window.inject(function(tasksApi, $httpBackend, $rootScope, $timeout) {
    service = tasksApi;
    $http = $httpBackend;
    timeout = $timeout;
    scope = $rootScope.$new();
  }));

  beforeEach(function() {
    $http.verifyNoOutstandingExpectation();
    $http.verifyNoOutstandingRequest();
  });

  describe('task watch methods', function() {

    function cycle() {
      timeout.flush();
      $http.flush();
      scope.$digest();
    }

    beforeEach(function() {
      task = service.one('applications', 'deck').one('tasks', 1).get().$object;
    });

    it('resolves watchForTaskComplete immediately if task is complete', function() {

      $http.whenGET('/applications/deck/tasks/1').respond(200, {
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
        requestHandler = $http.whenGET('/applications/deck/tasks/1');

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
        requestHandler = $http.whenGET('/applications/deck/tasks/1');

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
      task.get().then(function(newTask) {
        newTask.watchForTaskComplete().then(angular.noop, function(failedTask) { result = failedTask; });
      });

      cycle();

      // Step 2: STOPPED
      requestHandler.respond(200, { id: 1, status: 'STOPPED' });
      cycle();
      expect(result.id).toBe(1);
      expect(result.status).toBe('FAILED');
    });


    it('waits for force refresh step to complete, then resolves', function() {
      var result = null,
        requestHandler = $http.whenGET('/applications/deck/tasks/1');

      requestHandler.respond(200, { id: 1, status: 'RUNNING', steps: [] });
      $http.flush();

      task.watchForForceRefresh().then(function(updatedTask) { result = updatedTask; });

      // Step 1: No step found (retry)
      scope.$digest();
      expect(result).toBe(null);

      // Step 2: Step found, running (retry)
      requestHandler.respond(200, {
        id: 1,
        status: 'RUNNING',
        steps: [{
          name: 'forceCacheRefresh',
          status: 'RUNNING'
        }]
      });
      cycle();
      expect(result).toBe(null);

      // Step 3: Step found, completed
      requestHandler.respond(200, {
        id: 1,
        status: 'RUNNING',
        steps: [{
          name: 'forceCacheRefresh',
          status: 'COMPLETED'
        }]
      });

      cycle();
      expect(result.status).toBe('RUNNING');

    });

    it('rejects when force refresh step fails', function() {
      var result = null,
        requestHandler = $http.whenGET('/applications/deck/tasks/1');

      requestHandler.respond(200, { id: 1, status: 'RUNNING', steps: [] });
      $http.flush();

      task.watchForForceRefresh().then(angular.noop, function(updatedTask) { result = updatedTask; });

      // Step 1: No step found (retry)
      scope.$digest();
      expect(result).toBe(null);

      // Step 2: Step found, running (retry)
      requestHandler.respond(200, {
        id: 1,
        status: 'RUNNING',
        steps: [{
          name: 'forceCacheRefresh',
          status: 'STARTED'
        }]
      });
      cycle();
      expect(result).toBe(null);

      // Step 3: Step found, completed
      requestHandler.respond(200, {
        id: 1,
        status: 'RUNNING',
        steps: [{
          name: 'forceCacheRefresh',
          status: 'FAILED'
        }]
      });

      cycle();
      expect(result.status).toBe('RUNNING');
    });

    it('ignores completed status when force refresh is running or not started', function() {
      var result = null,
        requestHandler = $http.whenGET('/applications/deck/tasks/1');

      requestHandler.respond(200, { id: 1, status: 'RUNNING', steps: [] });
      $http.flush();

      task.watchForForceRefresh().then(function(updatedTask) { result = updatedTask; }, angular.noop);

      // Step 1: No step found (retry)
      cycle();

      expect(result).toBe(null);

      // Step 2: Still running (retry)
      requestHandler.respond(200, { id: 1, status: 'COMPLETED', steps: [{name: 'forceCacheRefresh', status: 'RUNNING'}] });
      cycle();

      expect(result).toBe(null);

      // Step 3: Complete
      requestHandler.respond(200, { id: 1, status: 'COMPLETED', steps: [{name: 'forceCacheRefresh', status: 'COMPLETED'}] });
      cycle();

      expect(result.status).toBe('COMPLETED');
    });

    it('cancels pending requests', function() {
      var result = null,
        requestHandler = $http.whenGET('/applications/deck/tasks/1');

      requestHandler.respond(200, { id: 1, status: 'RUNNING', steps: [] });
      $http.flush();

      task.watchForForceRefresh().then(angular.noop, function(updatedTask) { result = updatedTask; });

      // Step 1: No step found (retry)
      scope.$digest();
      expect(result).toBe(null);

      // Step 2: Step found, running (retry)
      requestHandler.respond(200, {
        id: 1,
        status: 'RUNNING',
        steps: [{
          name: 'forceCacheRefresh',
          status: 'STARTED'
        }]
      });
      cycle();
      expect(result).toBe(null);

      task.cancelPolls();

      cycle();

      $http.verifyNoOutstandingRequest();
    });

    it('appends kato task if not found, updates when found', function() {

      var katoTask = {
        id: 3,
        asOrcaKatoTask: function() {
          return { id: 3, updateable: 'a'};
        }
      };

      $http.whenGET('/applications/deck/tasks/1').respond(200, {
        id: 1,
        status: 'STARTED',
        steps: [],
        variables: []
      });
      $http.flush();

      task.updateKatoTask(katoTask);

      expect(task.getValueFor('kato.tasks').length).toBe(1);
      expect(task.getValueFor('kato.tasks')[0].id).toBe(3);
      expect(task.getValueFor('kato.tasks')[0].updateable).toBe('a');

      task.getValueFor('kato.tasks')[0].updateable = 'b';
      task.updateKatoTask(katoTask);
      expect(task.getValueFor('kato.tasks').length).toBe(1);
      expect(task.getValueFor('kato.tasks')[0].updateable).toBe('a');

      var katoTask2 = {
        id: 4,
        asOrcaKatoTask: function() {
          return {id: 4, history: []};
        }
      };

      task.updateKatoTask(katoTask2);
      expect(task.getValueFor('kato.tasks').length).toBe(2);
      expect(task.getValueFor('kato.tasks')[1].id).toBe(4);

    });
  });


  describe('task running time', function() {

    function execute() {
      service.one('applications', 'deck').one('tasks', 1).get().then(function(resolved) { task = resolved; });

      $http.flush();
      scope.$digest();
    }

    it('uses start time to calculate running time if endTime is zero', function() {
      $http.whenGET('/applications/deck/tasks/1').respond(200, {
        id: 2,
        status: 'COMPLETED',
        startTime: new Date(),
        endTime: 0
      });

      execute();

      expect(task.runningTime).toBe('a few seconds');
    });

    it('uses start time to calculate running time if endTime is not present', function() {
      $http.whenGET('/applications/deck/tasks/1').respond(200, {
        id: 2,
        status: 'COMPLETED',
        startTime: new Date()
      });

      execute();

      expect(task.runningTime).toBe('a few seconds');
    });

    it('calculates running time based on start and end times', function() {
      var start = new Date().getTime(),
          end = start + 120*1000;
      $http.whenGET('/applications/deck/tasks/1').respond(200, {
        id: 2,
        status: 'COMPLETED',
        startTime: start,
        endTime: end
      });

      execute();

      expect(task.runningTime).toBe('2 minutes');
    });
  });

});
