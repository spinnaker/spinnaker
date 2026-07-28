import type { IApplicationAttributes } from './ApplicationWriter';
import { ApplicationWriter } from './ApplicationWriter';
import type { IJob } from '../../task/taskExecutor';
import { TaskExecutor } from '../../task/taskExecutor';
import Spy = jasmine.Spy;

describe('ApplicationWriter', function () {
  describe('update an application', function () {
    it('should execute task', function () {
      spyOn(TaskExecutor, 'executeTask');

      const application: IApplicationAttributes = {
        name: 'foo',
        cloudProviders: [],
      };

      ApplicationWriter.updateApplication(application);

      expect((TaskExecutor.executeTask as Spy).calls.count()).toEqual(1);
    });

    it('should join cloud providers into a single string', function () {
      let job: IJob = null;
      spyOn(TaskExecutor, 'executeTask').and.callFake((task: any) => (job = task.job[0]));

      const application: IApplicationAttributes = {
        name: 'foo',
        cloudProviders: ['titus', 'cf'],
      };

      ApplicationWriter.updateApplication(application);

      expect(job).not.toBe(null);
      expect(job.application.cloudProviders).toBe('titus,cf');
    });
  });

  describe('delete an application', function () {
    it('should execute task', function () {
      spyOn(TaskExecutor, 'executeTask').and.returnValue(Promise.resolve({} as any));

      const application: IApplicationAttributes = {
        name: 'foo',
      };

      ApplicationWriter.deleteApplication(application);

      expect((TaskExecutor.executeTask as Spy).calls.count()).toEqual(1);
    });
  });
});
