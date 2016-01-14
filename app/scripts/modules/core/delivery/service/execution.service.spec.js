'use strict';

describe('Service: executionService', function () {

  var executionService;
  var $httpBackend;
  var settings;
  var timeout;
  var $q;

  beforeEach(
    window.module(
      require('./execution.service')
    )
  );

  beforeEach(
    window.inject(function (_executionService_, _$httpBackend_, _settings_, _$timeout_, _$q_) {
      executionService = _executionService_;
      $httpBackend = _$httpBackend_;
      settings = _settings_;
      timeout = _$timeout_;
      $q = _$q_;
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
      let cancelUrl = [ settings.gateUrl, 'applications', 'deck', 'pipelines', executionId, 'cancel' ].join('/');
      let checkUrl = [ settings.gateUrl, 'applications', 'deck', 'pipelines' ].join('/')
        .concat('?statuses=RUNNING,SUSPENDED,PAUSED,NOT_STARTED');
      let application = { name: 'deck', reloadExecutions: () => $q.when(null) };

      $httpBackend.expectPUT(cancelUrl).respond(200, []);
      $httpBackend.expectGET(checkUrl).respond(200, [{id: executionId}]);

      executionService.cancelExecution(application, executionId).then(() => completed = true);
      $httpBackend.flush();
      expect(completed).toBe(false);

      $httpBackend.expectGET(checkUrl).respond(200, [{id: 'some-other-execution'}]);
      timeout.flush();
      $httpBackend.flush();
      expect(completed).toBe(true);
    });

    it('should propagate rejection from failed cancel', function () {
      let failed = false;
      let executionId = 'abc';
      let cancelUrl = [ settings.gateUrl, 'applications', 'deck', 'pipelines', executionId, 'cancel' ].join('/');
      let application = { name: 'deck', reloadExecutions: () => $q.when(null) };

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
      let deleteUrl = [ settings.gateUrl, 'pipelines', executionId ].join('/');
      let checkUrl = [ settings.gateUrl, 'applications', 'deck', 'pipelines' ].join('/');
      let application = { name: 'deck', reloadExecutions: () => $q.when(null) };

      $httpBackend.expectDELETE(deleteUrl).respond(200, []);
      $httpBackend.expectGET(checkUrl).respond(200, [{id: executionId}]);

      executionService.deleteExecution(application, executionId).then(() => completed = true);
      $httpBackend.flush();
      expect(completed).toBe(false);

      $httpBackend.expectGET(checkUrl).respond(200, [{id: 'some-other-execution'}]);
      timeout.flush();
      $httpBackend.flush();
      expect(completed).toBe(true);
    });

    it('should propagate rejection from failed delete', function () {
      let failed = false;
      let executionId = 'abc';
      let deleteUrl = [ settings.gateUrl, 'pipelines', executionId ].join('/');
      let application = { name: 'deck', reloadExecutions: () => $q.when(null) };

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
      let pauseUrl = [ settings.gateUrl, 'pipelines', executionId, 'pause' ].join('/');
      let singleExecutionUrl = [ settings.gateUrl, 'pipelines', executionId ].join('/');
      let application = { name: 'deck', reloadExecutions: () => $q.when(null) };

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
      let pauseUrl = [ settings.gateUrl, 'pipelines', executionId, 'resume' ].join('/');
      let singleExecutionUrl = [ settings.gateUrl, 'pipelines', executionId ].join('/');
      let application = { name: 'deck', reloadExecutions: () => $q.when(null) };

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
          settings.gateUrl,
          'applications',
          'deck',
          'pipelines',
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
        settings.gateUrl,
        'applications',
        'deck',
        'pipelines',
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
          url = [settings.gateUrl, 'pipelines', executionId].join('/'),
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
          url = [settings.gateUrl, 'pipelines', executionId].join('/'),
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
          url = [settings.gateUrl, 'pipelines', executionId].join('/'),
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
});

