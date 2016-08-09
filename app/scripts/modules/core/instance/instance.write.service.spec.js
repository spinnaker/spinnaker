'use strict';

describe('Service: instance writer', function () {
  var service, serverGroupReader, taskExecutor, $q, $scope, MultiselectModel;

  beforeEach(
    window.module(
      require('./instance.write.service'),
      require('../cluster/filter/multiselect.model')
    )
  );

  beforeEach(
    window.inject(function(instanceWriter, _taskExecutor_, _serverGroupReader_, _$q_, $rootScope, _MultiselectModel_) {
      service = instanceWriter;
      taskExecutor = _taskExecutor_;
      serverGroupReader = _serverGroupReader_;
      $q = _$q_;
      $scope = $rootScope.$new();
      MultiselectModel = _MultiselectModel_;
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

  describe('multi-instance operations', function () {
    beforeEach(function () {
      this.task = null;
      this.serverGroupA = { type: 'aws', name: 'asg-v001', account: 'prod', region: 'us-east-1' };
      this.serverGroupB = { type: 'gce', name: 'asg-v002', account: 'test', region: 'us-west-1' };

      this.getInstanceGroup = (serverGroup) => {
        return MultiselectModel.getOrCreateInstanceGroup(serverGroup);
      };

      this.addInstance = (serverGroup, instance) => {
        let instanceGroup = this.getInstanceGroup(serverGroup);
        instanceGroup.instanceIds.push(instance.id);
        instanceGroup.instances.push(instance);
      };

      spyOn(taskExecutor, 'executeTask').and.callFake((task) => this.task = task);
    });

    it('only sends jobs for groups with instances', function () {
      let application = {};
      this.addInstance(this.serverGroupB, {id: 'i-234'});
      this.addInstance(this.serverGroupB, {id: 'i-345'});
      service.terminateInstances(
        [this.getInstanceGroup(this.serverGroupA), this.getInstanceGroup(this.serverGroupB)],
        application);

      expect(this.task.job.length).toBe(1);

      let job = this.task.job[0];

      expect(job.type).toBe('terminateInstances');
      expect(job.instanceIds).toEqual(['i-234', 'i-345']);
      expect(job.region).toBe('us-west-1');
      expect(job.cloudProvider).toBe('gce');
      expect(job.credentials).toBe('test');
      expect(job.serverGroupName).toBe('asg-v002');
    });

    it('includes additional job properties for terminate and shrink', function () {
      let application = {};
      this.addInstance(this.serverGroupA, {id: 'i-234'});
      this.addInstance(this.serverGroupA, {id: 'i-345'});
      service.terminateInstancesAndShrinkServerGroups(
        [this.getInstanceGroup(this.serverGroupA)],
        application);

      expect(this.task.job.length).toBe(1);

      let job = this.task.job[0];

      expect(job.type).toBe('detachInstances');
      expect(job.instanceIds).toEqual(['i-234', 'i-345']);
      expect(job.region).toBe('us-east-1');
      expect(job.cloudProvider).toBe('aws');
      expect(job.credentials).toBe('prod');
      expect(job.serverGroupName).toBe('asg-v001');
      expect(job.terminateDetachedInstances).toBe(true);
      expect(job.decrementDesiredCapacity).toBe(true);
    });

    it('includes a useful descriptor on terminate instances', function () {
      let application = {};
      this.addInstance(this.serverGroupA, {id: 'i-123', zone: 'a', launchTime: 2});

      service.terminateInstances([this.getInstanceGroup(this.serverGroupA)], application);
      expect(this.task.description).toBe('Terminate 1 instance');

      this.addInstance(this.serverGroupA, {id: 'i-1234', zone: 'a', launchTime: 1});
      service.terminateInstances([this.getInstanceGroup(this.serverGroupA)], application);
      expect(this.task.description).toBe('Terminate 2 instances');
    });

    it('includes a useful descriptor on reboot instances', function () {
      let application = {};
      this.addInstance(this.serverGroupA, {id: 'i-123', zone: 'a', launchTime: 2});

      service.rebootInstances([this.getInstanceGroup(this.serverGroupA)], application);
      expect(this.task.description).toBe('Reboot 1 instance');

      this.addInstance(this.serverGroupA, {id: 'i-1234', zone: 'a', launchTime: 1});
      service.rebootInstances([this.getInstanceGroup(this.serverGroupA)], application);
      expect(this.task.description).toBe('Reboot 2 instances');
    });

    it('includes a useful descriptor on disable in discovery', function () {
      let application = {};
      this.addInstance(this.serverGroupA, {id: 'i-123', zone: 'a', launchTime: 2});

      service.disableInstancesInDiscovery([this.getInstanceGroup(this.serverGroupA)], application);
      expect(this.task.description).toBe('Disable 1 instance in discovery');

      this.addInstance(this.serverGroupA, {id: 'i-1234', zone: 'a', launchTime: 1});
      service.disableInstancesInDiscovery([this.getInstanceGroup(this.serverGroupA)], application);
      expect(this.task.description).toBe('Disable 2 instances in discovery');
    });

    it('includes a useful descriptor on enable in discovery', function () {
      let application = {};
      this.addInstance(this.serverGroupA, {id: 'i-123', zone: 'a', launchTime: 2});

      service.enableInstancesInDiscovery([this.getInstanceGroup(this.serverGroupA)], application);
      expect(this.task.description).toBe('Enable 1 instance in discovery');

      this.addInstance(this.serverGroupA, {id: 'i-1234', zone: 'a', launchTime: 1});
      service.enableInstancesInDiscovery([this.getInstanceGroup(this.serverGroupA)], application);
      expect(this.task.description).toBe('Enable 2 instances in discovery');
    });
  });

});
