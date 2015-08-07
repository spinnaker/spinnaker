'use strict';

describe('Service: instance writer', function () {
  var service, serverGroupReader, taskExecutor, $q, $scope;

  beforeEach(
    window.module(
      require('./instance.write.service')
    )
  );

  beforeEach(
    window.inject(function(instanceWriter, _taskExecutor_, _serverGroupReader_, _$q_, $rootScope) {
      service = instanceWriter;
      taskExecutor = _taskExecutor_;
      serverGroupReader = _serverGroupReader_;
      $q = _$q_;
      $scope = $rootScope.$new();
    })
  );

  describe('terminate and decrement server group', function () {

    it('should set setMaxToNewDesired flag based on current server group capacity', function () {
      var serverGroup = {
        asg: {
          minSize: 4,
          maxSize: 4
        }
      };
      var instance = {
        instanceId: 'i-123456',
        account: 'test',
        region: 'us-east-1',
        serverGroup: 'asg-1',
      };
      var application = { name: 'app' };
      var executedTask = null;

      spyOn(taskExecutor, 'executeTask').and.callFake(function(task) {
        executedTask = task.job[0];
      });
      spyOn(serverGroupReader, 'getServerGroup').and.returnValue($q.when(serverGroup));


      service.terminateInstanceAndShrinkServerGroup(application, instance);
      $scope.$digest();
      $scope.$digest();

      expect(taskExecutor.executeTask).toHaveBeenCalled();
      expect(executedTask.setMaxToNewDesired).toBe(true);
    });

  });

});
