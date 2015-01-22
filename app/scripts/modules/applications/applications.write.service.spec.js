'use strict';

describe('Servcie: applicationWriter', function () {
  var applicationWriter;
  var taskExecutor;

  beforeEach(
    module(
      'deckApp.applications.write.service'
    )
  );

  beforeEach(
    inject(function(_applicationWriter_, _taskExecutor_) {
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
          pdApiKey: '229293'
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
        pdApiKey: '229293'
      };

      applicationWriter.updateApplication(application);

      expect(taskExecutor.executeTask).toHaveBeenCalled();
      expect(taskExecutor.executeTask.calls.count()).toEqual(2);
    });
  });

  describe('delete an application', function () {
    it('should execute one taks if the applicaiton has one account', function () {
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
