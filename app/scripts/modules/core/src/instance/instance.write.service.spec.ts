import { mock } from 'angular';

import { IMultiInstanceGroup, INSTANCE_WRITE_SERVICE, InstanceWriter } from 'core/instance/instance.write.service';
import { Application } from 'core/application/application.model';
import { ApplicationModelBuilder } from '../application/applicationModel.builder';
import { IInstance, IServerGroup } from 'core/domain';
import * as State from 'core/state';

import { ServerGroupReader } from '../serverGroup/serverGroupReader.service';
import { IJob, ITaskCommand, TaskExecutor } from '../task/taskExecutor';

describe('Service: instance writer', function() {
  let service: InstanceWriter, $q: ng.IQService, $scope: ng.IScope;

  beforeEach(() => State.initialize());

  beforeEach(mock.module(INSTANCE_WRITE_SERVICE));

  beforeEach(
    mock.inject((instanceWriter: InstanceWriter, _$q_: ng.IQService, $rootScope: ng.IRootScopeService) => {
      service = instanceWriter;
      $q = _$q_;
      $scope = $rootScope.$new();
    }),
  );

  describe('terminate and decrement server group', () => {
    it('should set setMaxToNewDesired flag based on current server group capacity', function() {
      const serverGroup = {
        asg: {
          minSize: 4,
          maxSize: 4,
        },
      };
      const instance: IInstance = {
        name: 'i-123456',
        id: 'i-123456',
        account: 'test',
        region: 'us-east-1',
        serverGroup: 'asg-1',
        health: [],
        healthState: 'Up',
        zone: 'a',
        launchTime: 2,
      };
      const application: Application = ApplicationModelBuilder.createApplicationForTests('app', {
        key: 'serverGroups',
        lazy: true,
      });
      let executedTask: IJob = null;

      spyOn(TaskExecutor, 'executeTask').and.callFake((task: ITaskCommand) => {
        executedTask = task.job[0];
      });
      spyOn(ServerGroupReader, 'getServerGroup').and.returnValue($q.when(serverGroup));

      service.terminateInstanceAndShrinkServerGroup(instance, application, {});
      $scope.$digest();
      $scope.$digest();

      expect(TaskExecutor.executeTask).toHaveBeenCalled();
      expect(executedTask['setMaxToNewDesired']).toBe(true);
    });
  });

  describe('multi-instance operations', () => {
    let task: ITaskCommand, serverGroupA: IServerGroup, serverGroupB: IServerGroup;

    function getInstanceGroup(serverGroup: IServerGroup): IMultiInstanceGroup {
      return State.ClusterState.multiselectModel.getOrCreateInstanceGroup(serverGroup);
    }

    function addInstance(serverGroup: IServerGroup, instance: IInstance) {
      const instanceGroup: IMultiInstanceGroup = getInstanceGroup(serverGroup);
      instanceGroup.instanceIds.push(instance.id);
      instanceGroup.instances.push(instance);
    }

    beforeEach(function() {
      task = null;
      serverGroupA = {
        type: 'aws',
        cloudProvider: 'aws',
        name: 'asg-v001',
        account: 'prod',
        region: 'us-east-1',
        cluster: 'asg',
        instanceCounts: null,
        instances: [],
      };
      serverGroupB = {
        type: 'gce',
        cloudProvider: 'gce',
        name: 'asg-v002',
        account: 'test',
        region: 'us-west-1',
        cluster: 'asg',
        instanceCounts: null,
        instances: [],
      };

      spyOn(TaskExecutor, 'executeTask').and.callFake((command: ITaskCommand) => (task = command));
    });

    it('only sends jobs for groups with instances', () => {
      const application: Application = ApplicationModelBuilder.createApplicationForTests('app');
      addInstance(serverGroupB, {
        name: 'i-234',
        id: 'i-234',
        health: [],
        healthState: 'Up',
        zone: 'a',
        launchTime: 2,
      });
      addInstance(serverGroupB, {
        name: 'i-345',
        id: 'i-345',
        health: [],
        healthState: 'Up',
        zone: 'a',
        launchTime: 2,
      });
      service.terminateInstances([getInstanceGroup(serverGroupA), getInstanceGroup(serverGroupB)], application);

      expect(task.job.length).toBe(1);

      const job = task.job[0];

      expect(job.type).toBe('terminateInstances');
      expect(job['instanceIds']).toEqual(['i-234', 'i-345']);
      expect(job['region']).toBe('us-west-1');
      expect(job['cloudProvider']).toBe('gce');
      expect(job['credentials']).toBe('test');
      expect(job['serverGroupName']).toBe('asg-v002');
    });

    it('includes additional job properties for terminate and shrink', () => {
      const application: Application = ApplicationModelBuilder.createApplicationForTests('app');
      addInstance(serverGroupA, {
        name: 'i-234',
        id: 'i-234',
        health: [],
        healthState: 'Up',
        zone: 'a',
        launchTime: 2,
      });
      addInstance(serverGroupA, {
        name: 'i-345',
        id: 'i-345',
        health: [],
        healthState: 'Up',
        zone: 'a',
        launchTime: 2,
      });
      service.terminateInstancesAndShrinkServerGroups([getInstanceGroup(serverGroupA)], application);

      expect(task.job.length).toBe(1);

      const job: IJob = task.job[0];

      expect(job.type).toBe('detachInstances');
      expect(job['instanceIds']).toEqual(['i-234', 'i-345']);
      expect(job['region']).toBe('us-east-1');
      expect(job['cloudProvider']).toBe('aws');
      expect(job['credentials']).toBe('prod');
      expect(job['serverGroupName']).toBe('asg-v001');
      expect(job['terminateDetachedInstances']).toBe(true);
      expect(job['decrementDesiredCapacity']).toBe(true);
    });

    it('includes a useful descriptor on terminate instances', () => {
      const application: Application = ApplicationModelBuilder.createApplicationForTests('app');
      addInstance(serverGroupA, {
        name: 'i-123',
        id: 'i-123',
        health: [],
        healthState: 'Up',
        zone: 'a',
        launchTime: 2,
      });

      service.terminateInstances([getInstanceGroup(serverGroupA)], application);
      expect(task.description).toBe('Terminate 1 instance');

      addInstance(serverGroupA, {
        name: 'i-1234',
        id: 'i-1234',
        health: [],
        healthState: 'Up',
        zone: 'a',
        launchTime: 1,
      });
      service.terminateInstances([getInstanceGroup(serverGroupA)], application);
      expect(task.description).toBe('Terminate 2 instances');
    });

    it('includes a useful descriptor on reboot instances', function() {
      const application: Application = ApplicationModelBuilder.createApplicationForTests('app');
      addInstance(serverGroupA, {
        name: 'i-123',
        id: 'i-123',
        health: [],
        healthState: 'Up',
        zone: 'a',
        launchTime: 2,
      });

      service.rebootInstances([getInstanceGroup(serverGroupA)], application);
      expect(task.description).toBe('Reboot 1 instance');

      addInstance(serverGroupA, {
        name: 'i-1234',
        id: 'i-1234',
        health: [],
        healthState: 'Up',
        zone: 'a',
        launchTime: 1,
      });
      service.rebootInstances([getInstanceGroup(serverGroupA)], application);
      expect(task.description).toBe('Reboot 2 instances');
    });

    it('includes a useful descriptor on disable in discovery', function() {
      const application: Application = ApplicationModelBuilder.createApplicationForTests('app');
      addInstance(serverGroupA, {
        name: 'i-123',
        id: 'i-123',
        health: [],
        healthState: 'Up',
        zone: 'a',
        launchTime: 2,
      });

      service.disableInstancesInDiscovery([getInstanceGroup(serverGroupA)], application);
      expect(task.description).toBe('Disable 1 instance in discovery');

      addInstance(serverGroupA, {
        name: 'i-1234',
        id: 'i-1234',
        health: [],
        healthState: 'Up',
        zone: 'a',
        launchTime: 1,
      });
      service.disableInstancesInDiscovery([getInstanceGroup(serverGroupA)], application);
      expect(task.description).toBe('Disable 2 instances in discovery');
    });

    it('includes a useful descriptor on enable in discovery', function() {
      const application: Application = ApplicationModelBuilder.createApplicationForTests('app');
      addInstance(serverGroupA, {
        name: 'i-123',
        id: 'i-123',
        health: [],
        healthState: 'Up',
        zone: 'a',
        launchTime: 2,
      });

      service.enableInstancesInDiscovery([getInstanceGroup(serverGroupA)], application);
      expect(task.description).toBe('Enable 1 instance in discovery');

      addInstance(serverGroupA, {
        name: 'i-1234',
        id: 'i-1234',
        health: [],
        healthState: 'Up',
        zone: 'a',
        launchTime: 1,
      });
      service.enableInstancesInDiscovery([getInstanceGroup(serverGroupA)], application);
      expect(task.description).toBe('Enable 2 instances in discovery');
    });
  });
});
