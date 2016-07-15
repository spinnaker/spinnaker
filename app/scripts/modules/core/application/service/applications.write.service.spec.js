'use strict';

describe('Service: applicationWriter', function () {
  var applicationWriter;
  var taskExecutor;
  var $q;

  beforeEach(
    window.module(
      require('./applications.write.service')
    )
  );

  beforeEach(
    window.inject(function(_applicationWriter_, _taskExecutor_, _$q_) {
      applicationWriter = _applicationWriter_;
      taskExecutor = _taskExecutor_;
      $q = _$q_;
    })
  );

  describe('update an application', function () {

    it('should execute one task for an application with one account', function () {
      spyOn(taskExecutor, 'executeTask');

      var application = {
          name: 'foo',
          accounts: 'test',
          description: 'foo description',
          email: 'foo@netflix.com',
          owner: 'jojo',
          type: 'test',
          pdApiKey: '229293',
          cloudProviders: [],
      };

      applicationWriter.updateApplication(application);

      expect(taskExecutor.executeTask).toHaveBeenCalled();
      expect(taskExecutor.executeTask.calls.count()).toEqual(1);
    });

    it('should execute a single task with multiple jobs for an application with multiple accounts', function () {
      var jobs = null;
      spyOn(taskExecutor, 'executeTask').and.callFake((task) => jobs = task.job);

      var application = {
        name: 'foo',
        accounts: 'test,prod',
        description: 'foo description',
        email: 'foo@netflix.com',
        owner: 'jojo',
        type: 'test',
        pdApiKey: '229293',
        cloudProviders: [],
      };

      applicationWriter.updateApplication(application);

      expect(taskExecutor.executeTask).toHaveBeenCalled();
      expect(taskExecutor.executeTask.calls.count()).toEqual(1);
      expect(jobs.length).toBe(2);
    });

    it('should join cloud providers into a single string', function () {
      var job = null;
      spyOn(taskExecutor, 'executeTask').and.callFake((task) => job = task.job[0]);

      var application = {
        name: 'foo',
        accounts: 'test',
        description: 'foo description',
        email: 'foo@netflix.com',
        owner: 'jojo',
        type: 'test',
        pdApiKey: '229293',
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

      var application = {
        name: 'foo',
        accounts: 'test',
      };

      applicationWriter.deleteApplication(application);

      expect(taskExecutor.executeTask).toHaveBeenCalled();
      expect(taskExecutor.executeTask.calls.count()).toEqual(1);
    });

    it('should execute a single task with multiple jobs for an application with multiple accounts', function () {
      var jobs = null;
      spyOn(taskExecutor, 'executeTask').and.callFake((task) => {
        jobs = task.job;
        return $q.when(task);
      });

      var application = {
        name: 'foo',
        accounts: 'test,prod',
      };

      applicationWriter.deleteApplication(application);

      expect(taskExecutor.executeTask).toHaveBeenCalled();
      expect(taskExecutor.executeTask.calls.count()).toEqual(1);
      expect(jobs.length).toBe(2);
    });
  });

});
