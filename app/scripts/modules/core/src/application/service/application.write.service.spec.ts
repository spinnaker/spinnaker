import {mock} from 'angular';

import {APPLICATION_WRITE_SERVICE, ApplicationWriter, IApplicationAttributes} from './application.write.service';
import {IJob} from 'core/task/taskExecutor';

describe('Service: applicationWriter', function () {
  let applicationWriter: ApplicationWriter;
  let taskExecutor: any;
  let $q: ng.IQService;

  beforeEach(
    mock.module(
      APPLICATION_WRITE_SERVICE
    )
  );

  beforeEach(
    mock.inject(function(_applicationWriter_: ApplicationWriter, _taskExecutor_: any, _$q_: ng.IQService) {
      applicationWriter = _applicationWriter_;
      taskExecutor = _taskExecutor_;
      $q = _$q_;
    })
  );

  describe('update an application', function () {

    it('should execute one task for an application with one account', function () {
      spyOn(taskExecutor, 'executeTask');

      const application: IApplicationAttributes = {
        name: 'foo',
        accounts: ['test'],
        cloudProviders: [],
      };

      applicationWriter.updateApplication(application);

      expect(taskExecutor.executeTask).toHaveBeenCalled();
      expect(taskExecutor.executeTask.calls.count()).toEqual(1);
    });

    it('should execute a single task with multiple jobs for an application with multiple accounts', function () {
      let jobs: IJob[] = [];
      spyOn(taskExecutor, 'executeTask').and.callFake((task: any) => jobs = task.job);

      const application: IApplicationAttributes = {
        name: 'foo',
        accounts: ['test', 'prod'],
        cloudProviders: [],
      };

      applicationWriter.updateApplication(application);

      expect(taskExecutor.executeTask).toHaveBeenCalled();
      expect(taskExecutor.executeTask.calls.count()).toEqual(1);
      expect(jobs.length).toBe(2);
    });

    it('should join cloud providers into a single string', function () {
      let job: IJob = null;
      spyOn(taskExecutor, 'executeTask').and.callFake((task: any) => job = task.job[0]);

      const application: IApplicationAttributes = {
        name: 'foo',
        accounts: ['test'],
        cloudProviders: ['titus', 'cf'],
      };

      applicationWriter.updateApplication(application);

      expect(job).not.toBe(null);
      expect(job.application.cloudProviders).toBe('titus,cf');

    });
  });

  describe('delete an application', function () {
    it('should execute one task if the application has one account', function () {
      spyOn(taskExecutor, 'executeTask').and.returnValue($q.when({}));

      const application: IApplicationAttributes = {
        name: 'foo',
        accounts: ['test'],
      };

      applicationWriter.deleteApplication(application);

      expect(taskExecutor.executeTask).toHaveBeenCalled();
      expect(taskExecutor.executeTask.calls.count()).toEqual(1);
    });

    it('should execute a single task with multiple jobs for an application with multiple accounts', function () {
      let jobs: IJob[] = [];
      spyOn(taskExecutor, 'executeTask').and.callFake((task: any) => {
        jobs = task.job;
        return $q.when(task);
      });

      const application: IApplicationAttributes = {
        name: 'foo',
        accounts: ['test', 'prod'],
      };

      applicationWriter.deleteApplication(application);

      expect(taskExecutor.executeTask).toHaveBeenCalled();
      expect(taskExecutor.executeTask.calls.count()).toEqual(1);
      expect(jobs.length).toBe(2);
    });
  });


});
