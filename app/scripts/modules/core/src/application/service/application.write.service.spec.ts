import { mock } from 'angular';

import { APPLICATION_WRITE_SERVICE, ApplicationWriter, IApplicationAttributes } from './application.write.service';
import { IJob, TaskExecutor } from 'core/task/taskExecutor';
import Spy = jasmine.Spy;

describe('Service: applicationWriter', function() {
  let applicationWriter: ApplicationWriter;
  let $q: ng.IQService;

  beforeEach(mock.module(APPLICATION_WRITE_SERVICE));

  beforeEach(
    mock.inject(function(_applicationWriter_: ApplicationWriter, _$q_: ng.IQService) {
      applicationWriter = _applicationWriter_;
      $q = _$q_;
    }),
  );

  describe('update an application', function() {
    it('should execute task', function() {
      spyOn(TaskExecutor, 'executeTask');

      const application: IApplicationAttributes = {
        name: 'foo',
        cloudProviders: [],
      };

      applicationWriter.updateApplication(application);

      expect((TaskExecutor.executeTask as Spy).calls.count()).toEqual(1);
    });

    it('should join cloud providers into a single string', function() {
      let job: IJob = null;
      spyOn(TaskExecutor, 'executeTask').and.callFake((task: any) => (job = task.job[0]));

      const application: IApplicationAttributes = {
        name: 'foo',
        cloudProviders: ['titus', 'cf'],
      };

      applicationWriter.updateApplication(application);

      expect(job).not.toBe(null);
      expect(job.application.cloudProviders).toBe('titus,cf');
    });
  });

  describe('delete an application', function() {
    it('should execute task', function() {
      spyOn(TaskExecutor, 'executeTask').and.returnValue($q.when({}));

      const application: IApplicationAttributes = {
        name: 'foo',
      };

      applicationWriter.deleteApplication(application);

      expect((TaskExecutor.executeTask as Spy).calls.count()).toEqual(1);
    });
  });
});
