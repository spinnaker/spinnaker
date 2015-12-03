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
        .concat('?statuses=RUNNING,SUSPENDED,NOT_STARTED');
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

  describe('when fetching pipelines', function () {

    it('should resolve the promise if a 200 response is received with empty array', function(){
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

    it('should reject the promise if a 429 response is received', function(){
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
});

