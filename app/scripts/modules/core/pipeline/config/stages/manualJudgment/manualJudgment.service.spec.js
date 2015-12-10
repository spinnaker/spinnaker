'use strict';

describe('Service: manualJudgment', function () {

  var $scope, service, $http, $q, settings, executionService;

  beforeEach(
    window.module(
      require('./manualJudgment.service')
    )
  );

  beforeEach(
    window.inject(function ($rootScope, manualJudgmentService, $httpBackend, _$q_, _settings_,
                            _executionService_) {
      $scope = $rootScope.$new();
      service = manualJudgmentService;
      $http = $httpBackend;
      $q = _$q_;
      settings = _settings_;
      executionService = _executionService_;
    })
  );

  describe('provideJudgment', function () {
    beforeEach(function() {
      this.execution = { id: 'ex-id' };
      this.stage = { id: 'stage-id' };
      this.requestUrl = [settings.gateUrl, 'pipelines', this.execution.id, 'stages', this.stage.id].join('/');
    });

    it('should resolve when execution status matches request', function () {
      let deferred = $q.defer(),
          succeeded = false;

      $http.expectPATCH(this.requestUrl).respond(200, '');
      spyOn(executionService, 'waitUntilExecutionMatches').and.returnValue(deferred.promise);

      service.provideJudgment(this.execution, this.stage, 'continue').then(() => succeeded = true);

      $http.flush();
      expect(succeeded).toBe(false);

      // waitForExecutionMatches...
      deferred.resolve();
      $scope.$digest();

      expect(succeeded).toBe(true);
    });

    it('should fail when waitUntilExecutionMatches fails', function () {
      let deferred = $q.defer(),
          succeeded = false,
          failed = false;

      $http.expectPATCH(this.requestUrl).respond(200, '');
      spyOn(executionService, 'waitUntilExecutionMatches').and.returnValue(deferred.promise);

      service.provideJudgment(this.execution, this.stage, 'continue').then(() => succeeded = true, () => failed = true);

      $http.flush();
      expect(succeeded).toBe(false);
      expect(failed).toBe(false);

      // waitForExecutionMatches...
      deferred.reject();
      $scope.$digest();

      expect(succeeded).toBe(false);
      expect(failed).toBe(true);
    });

    it('should fail when patch call fails', function () {
      let succeeded = false,
          failed = false;

      $http.expectPATCH(this.requestUrl).respond(503, '');

      service.provideJudgment(this.execution, this.stage, 'continue').then(() => succeeded = true, () => failed = true);

      $http.flush();
      expect(succeeded).toBe(false);
      expect(failed).toBe(true);
    });

  });
});
