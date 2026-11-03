import type { Mock } from 'vitest';
import type { IApplicationAttributes } from './ApplicationWriter';
import { ApplicationWriter } from './ApplicationWriter';
import type { IJob } from '../../task/taskExecutor';
import { TaskExecutor } from '../../task/taskExecutor';
import Spy = Mock;

describe('ApplicationWriter', function () {
  describe('update an application', function () {
    it('should execute task', function () {
      vi.spyOn(TaskExecutor, 'executeTask').mockReturnValue(undefined);

      const application: IApplicationAttributes = {
        name: 'foo',
        cloudProviders: [],
      };

      ApplicationWriter.updateApplication(application);

      expect((TaskExecutor.executeTask as Spy).mock.calls.length).toEqual(1);
    });

    it('should join cloud providers into a single string', function () {
      let job: IJob = null;
      vi.spyOn(TaskExecutor, 'executeTask').mockImplementation((task: any) => (job = task.job[0]));

      const application: IApplicationAttributes = {
        name: 'foo',
        cloudProviders: ['ecs', 'cf'],
      };

      ApplicationWriter.updateApplication(application);

      expect(job).not.toBe(null);
      expect(job.application.cloudProviders).toBe('ecs,cf');
    });
  });

  describe('delete an application', function () {
    it('should execute task', function () {
      vi.spyOn(TaskExecutor, 'executeTask').mockReturnValue(Promise.resolve({} as any));

      const application: IApplicationAttributes = {
        name: 'foo',
      };

      ApplicationWriter.deleteApplication(application);

      expect((TaskExecutor.executeTask as Spy).mock.calls.length).toEqual(1);
    });
  });
});
