import { mockHttpClient } from 'core/api/mock/jasmine';
import { IDeferred, IHttpBackendService, IQService, IRootScopeService, IScope, mock } from 'angular';

import { SETTINGS } from 'core/config/settings';
import { MANUAL_JUDGMENT_SERVICE, ManualJudgmentService } from './manualJudgment.service';
import { ExecutionService } from 'core/pipeline/service/execution.service';

describe('Service: manualJudgment', () => {
  let $scope: IScope,
    service: ManualJudgmentService,
    $httpBackend: IHttpBackendService,
    $q: IQService,
    executionService: ExecutionService;

  beforeEach(mock.module(MANUAL_JUDGMENT_SERVICE, 'ui.router'));

  beforeEach(
    mock.inject(
      (
        $rootScope: IRootScopeService,
        manualJudgmentService: ManualJudgmentService,
        _$httpBackend_: IHttpBackendService,
        _$q_: IQService,
        _executionService_: ExecutionService,
      ) => {
        $scope = $rootScope.$new();
        service = manualJudgmentService;
        $httpBackend = _$httpBackend_;
        $q = _$q_;
        executionService = _executionService_;
      },
    ),
  );

  describe('provideJudgment', () => {
    let execution: any, stage: any, requestUrl: string;
    beforeEach(() => {
      execution = { id: 'ex-id' };
      stage = { id: 'stage-id' };
      requestUrl = [SETTINGS.gateUrl, 'pipelines', execution.id, 'stages', stage.id].join('/');
    });

    it('should resolve when execution status matches request', async () => {
      const http = mockHttpClient();
      const deferred: IDeferred<boolean> = $q.defer();
      let succeeded = false;

      http.expectPATCH(requestUrl).respond(200, '');
      spyOn(executionService, 'waitUntilExecutionMatches').and.returnValue(deferred.promise);
      spyOn(executionService, 'updateExecution').and.stub();

      service.provideJudgment(null, execution, stage, 'continue').then(() => (succeeded = true));

      await http.flush();
      expect(succeeded).toBe(false);

      // waitForExecutionMatches...
      deferred.resolve();
      $scope.$digest();

      expect(succeeded).toBe(true);
    });

    it('should fail when waitUntilExecutionMatches fails', async () => {
      const http = mockHttpClient();
      const deferred: IDeferred<boolean> = $q.defer();
      let succeeded = false,
        failed = false;

      http.expectPATCH(requestUrl).respond(200, '');
      spyOn(executionService, 'waitUntilExecutionMatches').and.returnValue(deferred.promise);

      service.provideJudgment(null, execution, stage, 'continue').then(
        () => (succeeded = true),
        () => (failed = true),
      );

      await http.flush();
      expect(succeeded).toBe(false);
      expect(failed).toBe(false);

      // waitForExecutionMatches...
      deferred.reject();
      $scope.$digest();

      expect(succeeded).toBe(false);
      expect(failed).toBe(true);
    });

    it('should fail when patch call fails', async () => {
      const http = mockHttpClient();
      let succeeded = false,
        failed = false;

      http.expectPATCH(requestUrl).respond(503, '');

      service.provideJudgment(null, execution, stage, 'continue').then(
        () => (succeeded = true),
        () => (failed = true),
      );

      await http.flush();
      expect(succeeded).toBe(false);
      expect(failed).toBe(true);
    });
  });
});
