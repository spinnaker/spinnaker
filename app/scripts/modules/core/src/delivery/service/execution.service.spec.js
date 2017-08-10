'use strict';

import {EXECUTION_SERVICE} from './execution.service';
import {SETTINGS} from 'core/config/settings';

describe('Service: executionService', function () {

  var executionService;
  var $httpBackend;
  var timeout;
  var $q;

  beforeEach(
    window.module(
      EXECUTION_SERVICE
    )
  );

  // https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$q
  beforeEach(
    window.module(($qProvider) => {
      $qProvider.errorOnUnhandledRejections(false);
  }));

  beforeEach(
    window.inject(function (_executionService_, _$httpBackend_, _$timeout_, _$q_, executionFilterModel) {
      executionService = _executionService_;
      $httpBackend = _$httpBackend_;
      timeout = _$timeout_;
      $q = _$q_;
      executionFilterModel.sortFilter.count = 3;
    })
  );

  afterEach(function() {
    $httpBackend.verifyNoOutstandingExpectation();
    $httpBackend.verifyNoOutstandingRequest();
  });

  describe('cancelling pipeline', function () {
    it('should wait until pipeline is not running, then resolve', function () {
      let completed = false;
      let executionId = 'abc';
      let cancelUrl = [ SETTINGS.gateUrl, 'pipelines', executionId, 'cancel' ].join('/');
      let checkUrl = [ SETTINGS.gateUrl, 'pipelines', executionId ].join('/');
      let application = { name: 'deck', executions: { refresh: () => $q.when(null) } };

      $httpBackend.expectPUT(cancelUrl).respond(200, []);
      $httpBackend.expectGET(checkUrl).respond(200, {id: executionId, status: 'RUNNING'});

      executionService.cancelExecution(application, executionId).then(() => completed = true);
      $httpBackend.flush();
      expect(completed).toBe(false);

      $httpBackend.expectGET(checkUrl).respond(200, {id: executionId, status: 'CANCELED'});
      timeout.flush();
      $httpBackend.flush();
      expect(completed).toBe(true);
    });

    it('should propagate rejection from failed cancel', function () {
      let failed = false;
      let executionId = 'abc';
      let cancelUrl = [ SETTINGS.gateUrl, 'pipelines', executionId, 'cancel' ].join('/');
      let application = { name: 'deck', executions: { refresh: () => $q.when(null) } };

      $httpBackend.expectPUT(cancelUrl).respond(500, []);

      executionService.cancelExecution(application, executionId).then(angular.noop, () => failed = true);
      $httpBackend.flush();
      expect(failed).toBe(true);
    });
  });

  describe('deleting pipeline', function () {
    it('should wait until pipeline is missing, then resolve', function () {
      let completed = false;
      let executionId = 'abc';
      let deleteUrl = [ SETTINGS.gateUrl, 'pipelines', executionId ].join('/');
      let checkUrl = [ SETTINGS.gateUrl, 'pipelines', executionId ].join('/');
      let application = { name: 'deck', executions: { refresh: () => $q.when(null) } };

      $httpBackend.expectDELETE(deleteUrl).respond(200, []);
      $httpBackend.expectGET(checkUrl).respond(200, {id: executionId});

      executionService.deleteExecution(application, executionId).then(() => completed = true);
      $httpBackend.flush();
      expect(completed).toBe(false);

      $httpBackend.expectGET(checkUrl).respond(404, null);
      timeout.flush();
      $httpBackend.flush();
      expect(completed).toBe(true);
    });

    it('should propagate rejection from failed delete', function () {
      let failed = false;
      let executionId = 'abc';
      let deleteUrl = [ SETTINGS.gateUrl, 'pipelines', executionId ].join('/');
      let application = { name: 'deck', executions: { refresh: () => $q.when(null) } };

      $httpBackend.expectDELETE(deleteUrl).respond(500, []);

      executionService.deleteExecution(application, executionId).then(angular.noop, () => failed = true);
      $httpBackend.flush();
      expect(failed).toBe(true);
    });
  });

  describe('pausing pipeline', function () {
    it('should wait until pipeline is PAUSED, then resolve', function () {
      let completed = false;
      let executionId = 'abc';
      let pauseUrl = [ SETTINGS.gateUrl, 'pipelines', executionId, 'pause' ].join('/');
      let singleExecutionUrl = [ SETTINGS.gateUrl, 'pipelines', executionId ].join('/');
      let application = { name: 'deck', executions: { refresh: () => $q.when(null) } };

      $httpBackend.expectPUT(pauseUrl).respond(200, []);
      $httpBackend.expectGET(singleExecutionUrl).respond(200, {id: executionId, status: 'RUNNING'});

      executionService.pauseExecution(application, executionId).then(() => completed = true);
      $httpBackend.flush();
      expect(completed).toBe(false);

      $httpBackend.expectGET(singleExecutionUrl).respond(200, {id: executionId, status: 'PAUSED'});
      timeout.flush();
      $httpBackend.flush();

      expect(completed).toBe(true);
    });
  });

  describe('resuming pipeline', function () {
    it('should wait until pipeline is RUNNING, then resolve', function () {
      let completed = false;
      let executionId = 'abc';
      let pauseUrl = [ SETTINGS.gateUrl, 'pipelines', executionId, 'resume' ].join('/');
      let singleExecutionUrl = [ SETTINGS.gateUrl, 'pipelines', executionId ].join('/');
      let application = { name: 'deck', executions: { refresh: () => $q.when(null) } };

      $httpBackend.expectPUT(pauseUrl).respond(200, []);
      $httpBackend.expectGET(singleExecutionUrl).respond(200, {id: executionId, status: 'PAUSED'});

      executionService.resumeExecution(application, executionId).then(() => completed = true);
      $httpBackend.flush();
      expect(completed).toBe(false);

      $httpBackend.expectGET(singleExecutionUrl).respond(200, {id: executionId, status: 'RUNNING'});
      timeout.flush();
      $httpBackend.flush();

      expect(completed).toBe(true);
    });
  });

  describe('when fetching pipelines', function () {

    it('should resolve the promise if a 200 response is received with empty array', function() {
      let url = [
          SETTINGS.gateUrl,
          'applications',
          'deck',
          'pipelines?limit=3',
        ].join('/');

      $httpBackend.expectGET(url).respond(200, []);

      let responsePromise = executionService.getExecutions('deck');

      $httpBackend.flush();

      responsePromise
        .then((result) => {
          expect(result).toBeDefined();// only success should be called
          expect(result).toEqual([]);
        })
        .catch((reject) => {
          expect(reject).toBeUndefined();
        });
    });

    it('should reject the promise if a 429 response is received', function() {
      let url = [
        SETTINGS.gateUrl,
        'applications',
        'deck',
        'pipelines?limit=3',
      ].join('/');

      $httpBackend.expectGET(url).respond(429, []);

      let responsePromise = executionService.getExecutions('deck');

      $httpBackend.flush();

      responsePromise
        .then((result) => {
          expect(result).toBeUndefined();
        })
        .catch((result) => {
          expect(result).toBeDefined();// only reject should be called
        });
    });
  });

  describe('waitUntilExecutionMatches', function () {

    it('resolves when the execution matches the closure', function () {
      let executionId = 'abc',
          url = [SETTINGS.gateUrl, 'pipelines', executionId].join('/'),
          succeeded = false;

      $httpBackend.expectGET(url).respond(200, { thingToMatch: true });

      executionService.waitUntilExecutionMatches(executionId, (execution) => execution.thingToMatch)
      .then(() => succeeded = true);

      expect(succeeded).toBe(false);

      $httpBackend.flush();
      expect(succeeded).toBe(true);
    });

    it('polls until the execution matches, then resolves', function () {
      let executionId = 'abc',
          url = [SETTINGS.gateUrl, 'pipelines', executionId].join('/'),
          succeeded = false;

      $httpBackend.expectGET(url).respond(200, { thingToMatch: false });

      executionService.waitUntilExecutionMatches(executionId, (execution) => execution.thingToMatch)
        .then(() => succeeded = true);

      expect(succeeded).toBe(false);

      $httpBackend.flush();
      expect(succeeded).toBe(false);

      // no match, retrying
      $httpBackend.expectGET(url).respond(200, { thingToMatch: false });
      timeout.flush();
      $httpBackend.flush();

      expect(succeeded).toBe(false);

      // still no match, retrying again
      $httpBackend.expectGET(url).respond(200, { thingToMatch: true });
      timeout.flush();
      $httpBackend.flush();

      expect(succeeded).toBe(true);
    });

    it('rejects if execution retrieval fails', function () {
      let executionId = 'abc',
          url = [SETTINGS.gateUrl, 'pipelines', executionId].join('/'),
          succeeded = false,
          failed = false;

      $httpBackend.expectGET(url).respond(200, { thingToMatch: false });

      executionService.waitUntilExecutionMatches(executionId, (execution) => execution.thingToMatch)
        .then(() => succeeded = true, () => failed = true);

      expect(succeeded).toBe(false);
      expect(failed).toBe(false);

      $httpBackend.flush();

      // no match, retrying
      expect(succeeded).toBe(false);
      expect(failed).toBe(false);
      $httpBackend.expectGET(url).respond(500, '');
      timeout.flush();
      $httpBackend.flush();
      expect(succeeded).toBe(false);
      expect(failed).toBe(true);
    });
  });

  describe('merging executions and running executions', () => {
    let application;
    let dataUpdated = false;
    beforeEach(function() {
      dataUpdated = false;
      application = {
        executions: { data: [], dataUpdated: () => dataUpdated = true },
        runningExecutions: { data: [], dataUpdated: () => dataUpdated = true }
      };
    });

    describe('removeCompletedExecutionsFromRunningData', () => {
      it('should remove executions that have completed', () => {
        application.executions.data = [ {id: 0, isActive: false}, {id: 1, isActive: false}, {id: 2, isActive: true} ];
        application.runningExecutions.data = [ {id: 1, isActive: true}, {id: 2, isActive: true} ];
        executionService.removeCompletedExecutionsFromRunningData(application);
        expect(application.runningExecutions.data.map(d => d.id)).toEqual([2]);
        expect(dataUpdated).toBe(true);
      });
    });

    describe('mergeRunningExecutionsIntoExecutions', () => {
      it('should add running executions to executions, and update if stringVal changed', () => {
        application.executions.data = [ {id: 0, isActive: false, stringVal: 'a'}, {id: 2, isActive: true, stringVal: 'b'} ];
        application.runningExecutions.data = [ {id: 1, isActive: true, stringVal: 'c'}, {id: 2, isActive: true, stringVal: 'd'} ];
        executionService.mergeRunningExecutionsIntoExecutions(application);
        expect(application.executions.data.map(d => `${d.id}:${d.stringVal}`)).toEqual(['0:a', '2:d', '1:c']);
        expect(dataUpdated).toBe(true);
      });

      it('should only call dataUpdated if actual updates occurred', () => {
        application.executions.data = [ {id: 0, isActive: false, stringVal: 'a'}, {id: 2, isActive: true, stringVal: 'b'} ];
        application.runningExecutions.data = [ {id: 2, isActive: true, stringVal: 'b'} ];
        executionService.mergeRunningExecutionsIntoExecutions(application);
        expect(application.executions.data.map(d => `${d.id}:${d.stringVal}`)).toEqual(['0:a', '2:b']);
        expect(dataUpdated).toBe(false);
      });

    });
  });

  describe('adding executions to applications', function () {
    var application;
    beforeEach(function() {
      application = { executions: { data: [] }, runningExecutions: { data: [] }};
    });
    it('should add all executions if there are none on application', function () {
      let execs = [{a:1}];
      let data = executionService.addExecutionsToApplication(application, execs);

      expect(data).toBe(execs);
    });

    it('should add new executions', function () {
      let original = {id:1, stringVal: 'ac'};
      let newOne = {id:2, stringVal: 'ab'};
      let execs = [original, newOne];
      application.executions.data = [original];

      executionService.addExecutionsToApplication(application, execs);

      expect(application.executions.data).toEqual([original, newOne]);
    });

    it('should update mutated states in an existing execution if stringVal has changed', function () {
      var originalStages = [
        { id: 'a', status: 'COMPLETED' },
        { id: 'b', status: 'RUNNING' },
        { id: 'c', status: 'RUNNING' },
        { id: 'd', status: 'NOT_STARTED' }
      ];
      var updatedStages = [
        { id: 'a', status: 'COMPLETED' },
        { id: 'b', status: 'RUNNING', newField: 'x' },
        { id: 'c', status: 'RUNNING' },
        { id: 'd', status: 'NOT_STARTED' }
      ];
      let original = {
        id:1,
        stringVal: 'ac',
        stageSummaries: originalStages.slice(),
        graphStatusHash: 'COMPLETED:RUNNING:RUNNING:NOT_STARTED',
      };
      let updated = {
        id:1,
        stringVal: 'ab',
        stageSummaries: updatedStages.slice(),
        graphStatusHash: 'COMPLETED:RUNNING:RUNNING:NOT_STARTED',
      };
      let execs = [updated];
      application.executions.data = [original];

      executionService.addExecutionsToApplication(application, execs);

      expect(application.executions.data).toEqual([updated]);
      expect(application.executions.data).not.toBe([updated]);
      expect(application.executions.data[0].stageSummaries[0]).toBe(originalStages[0]);
      expect(application.executions.data[0].stageSummaries[0]).toEqual(updatedStages[0]);
      expect(application.executions.data[0].stageSummaries[1]).toBe(originalStages[1]);
      expect(application.executions.data[0].stageSummaries[1]).toEqual(updatedStages[1]);
      expect(application.executions.data[0].stageSummaries[2]).toBe(originalStages[2]);
      expect(application.executions.data[0].stageSummaries[2]).toEqual(updatedStages[2]);
      expect(application.executions.data[0].stageSummaries[3]).toBe(originalStages[3]);
      expect(application.executions.data[0].stageSummaries[3]).toEqual(updatedStages[3]);

    });

    it('should replace an existing execution if status changes', function () {
      let original = {id:1, stringVal: 'ac', status: 'RUNNING'};
      let updated = {id:1, stringVal: 'ab', status: 'COMPLETED'};
      let execs = [updated];
      application.executions.data = [original];

      executionService.addExecutionsToApplication(application, execs);

      expect(application.executions.data).toEqual([updated]);
    });

    it('should remove an execution if it is not in the new set', function () {
      let transient = {id:1, stringVal: 'ac'};
      let persistent = {id:2, stringVal: 'ab'};
      let execs = [persistent];
      application.executions.data = [transient];

      executionService.addExecutionsToApplication(application, execs);

      expect(application.executions.data).toEqual([persistent]);
    });

    it('should retain running executions, even if they are not in the new set', function () {
      let running = {id:3};
      application.executions.data = [running];
      application.runningExecutions.data = [running];

      executionService.addExecutionsToApplication(application, []);
      expect(application.executions.data).toEqual([running]);
    });

    it('should remove multiple executions if not in the new set', function () {
      let transient1 = {id:1, stringVal: 'ac'};
      let persistent = {id:2, stringVal: 'ab'};
      let transient3 = {id:3, stringVal: 'ac'};
      let execs = [persistent];
      application.executions.data = [transient1, persistent, transient3];

      executionService.addExecutionsToApplication(application, execs);

      expect(application.executions.data).toEqual([persistent]);
    });

    it('should replace the existing executions if application has executions comes back empty', function () {
      let execs = [];
      application.executions.data = [{a:1}];

      let data = executionService.addExecutionsToApplication(application, execs);

      expect(data).toEqual([]);
    });

  });
});
