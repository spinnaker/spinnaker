'use strict';

describe('Servcie: applicationWriter', function () {
  var applicationWriter;
  var taskExecutor;

  beforeEach(
    window.module(
      require('./applications.write.service')
    )
  );

  beforeEach(
    window.inject(function(_applicationWriter_, _taskExecutor_) {
      applicationWriter = _applicationWriter_;
      taskExecutor = _taskExecutor_;
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

    it('should execute a task for each account in a application', function () {
      spyOn(taskExecutor, 'executeTask');

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
      expect(taskExecutor.executeTask.calls.count()).toEqual(2);
    });

    it('should join cloud providers into a single string', function () {
      var job;
      spyOn(taskExecutor, 'executeTask').and.callFake((task) => job = task.job[0]);

      var application = {
        name: 'foo',
        accounts: 'test',
        description: 'foo description',
        email: 'foo@netflix.com',
        owner: 'jojo',
        type: 'test',
        pdApiKey: '229293',
        cloudProviders: ['titan', 'cf'],
      };

      applicationWriter.updateApplication(application);

      expect(job.application.cloudProviders).toBe('titan,cf');

    });
  });

  describe('delete an application', function () {
    it('should execute one task if the application has one account', function () {
      spyOn(taskExecutor, 'executeTask');

      var application = {
        name: 'foo',
        accounts: 'test',
      };

      applicationWriter.deleteApplication(application);

      expect(taskExecutor.executeTask).toHaveBeenCalled();
      expect(taskExecutor.executeTask.calls.count()).toEqual(1);
    });
  });

});
