import { ManualJudgmentService } from './manualJudgment.service';
import type { ExecutionService } from '../../../service/execution.service';

describe('Service: manualJudgment', () => {
  let service: ManualJudgmentService, executionService: jasmine.SpyObj<ExecutionService>;

  beforeEach(() => {
    executionService = jasmine.createSpyObj<ExecutionService>('executionService', [
      'patchExecution',
      'waitUntilExecutionMatches',
      'updateExecution',
    ]);
    service = new ManualJudgmentService(executionService);
  });

  describe('provideJudgment', () => {
    let application: any, execution: any, stage: any, updatedExecution: any;
    beforeEach(() => {
      application = { name: 'app' };
      execution = { id: 'ex-id' };
      stage = { id: 'stage-id' };
      updatedExecution = { stages: [{ id: 'stage-id', status: 'SUCCEEDED' }] };
    });

    it('patches the judgment, waits for the stage to finish running, and updates the execution', async () => {
      executionService.patchExecution.and.returnValue(Promise.resolve(null) as any);
      executionService.waitUntilExecutionMatches.and.returnValue(Promise.resolve(updatedExecution) as any);
      executionService.updateExecution.and.returnValue(Promise.resolve(null) as any);

      await service.provideJudgment(application, execution, stage, 'continue', 'ship it');

      expect(executionService.patchExecution).toHaveBeenCalledWith('ex-id', 'stage-id', {
        judgmentStatus: 'continue',
        judgmentInput: 'ship it',
      });
      const matcher = executionService.waitUntilExecutionMatches.calls.mostRecent().args[1];
      expect(matcher({ stages: [{ id: 'stage-id', status: 'RUNNING' }] } as any)).toBe(false);
      expect(matcher(updatedExecution)).toBe(true);
      expect(executionService.updateExecution).toHaveBeenCalledWith(application, updatedExecution);
    });

    it('fails when waitUntilExecutionMatches fails', async () => {
      const error = new Error('wait failed');
      executionService.patchExecution.and.returnValue(Promise.resolve(null) as any);
      executionService.waitUntilExecutionMatches.and.returnValue(Promise.reject(error) as any);

      await expectAsync(service.provideJudgment(application, execution, stage, 'continue')).toBeRejectedWith(error);
      expect(executionService.updateExecution).not.toHaveBeenCalled();
    });

    it('fails when patch call fails', async () => {
      const error = new Error('patch failed');
      executionService.patchExecution.and.returnValue(Promise.reject(error) as any);

      await expectAsync(service.provideJudgment(application, execution, stage, 'continue')).toBeRejectedWith(error);
      expect(executionService.waitUntilExecutionMatches).not.toHaveBeenCalled();
    });
  });
});
