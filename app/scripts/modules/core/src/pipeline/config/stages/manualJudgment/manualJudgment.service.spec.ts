import { mockHttpClient } from '../../../../api/mock/jasmine';
import { tick } from '../../../../api/mock/mockHttpUtils';
import { IDeferred, IQService, IRootScopeService, IScope, mock } from 'angular';

import { MANUAL_JUDGMENT_SERVICE, ManualJudgmentService } from './manualJudgment.service';
import { ExecutionService } from '../../../service/execution.service';

describe('Service: manualJudgment', () => {
  let $scope: IScope, service: ManualJudgmentService, $q: IQService, executionService: ExecutionService;

  beforeEach(mock.module(MANUAL_JUDGMENT_SERVICE, 'ui.router'));

  beforeEach(
    mock.inject(
      (
        $rootScope: IRootScopeService,
        manualJudgmentService: ManualJudgmentService,
        _$q_: IQService,
        _executionService_: ExecutionService,
      ) => {
        $scope = $rootScope.$new();
        service = manualJudgmentService;
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
      requestUrl = `/pipelines/${execution.id}/stages/${stage.id}`;
    });

    it('should resolve when execution status matches request', async () => {
      const http = mockHttpClient();
      const deferred: IDeferred<boolean> = $q.defer();
      let succeeded = false;

      http.expectPATCH(requestUrl).respond(200, '');
      spyOn(executionService, 'waitUntilExecutionMatches').and.returnValue(deferred.promise as any);
      spyOn(executionService, 'updateExecution').and.stub();

      service.provideJudgment(null, execution, stage, 'continue').then(() => (succeeded = true));

      await http.flush();
      expect(succeeded).toBe(false);

      // waitForExecutionMatches...
      deferred.resolve();
      $scope.$digest();
      await tick();

      expect(succeeded).toBe(true);
    });

    it('should fail when waitUntilExecutionMatches fails', async () => {
      const http = mockHttpClient();
      const deferred: IDeferred<boolean> = $q.defer();
      let succeeded = false;
      let failed = false;

      http.expectPATCH(requestUrl).respond(200, '');
      spyOn(executionService, 'waitUntilExecutionMatches').and.returnValue(deferred.promise as any);

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
      await tick();

      expect(succeeded).toBe(false);
      expect(failed).toBe(true);
    });

    it('should fail when patch call fails', async () => {
      const http = mockHttpClient();
      let succeeded = false;
      let failed = false;

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
