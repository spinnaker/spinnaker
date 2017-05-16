import {mock} from 'angular';
import { StateService } from '@uirouter/core';

import { ClusterFilterModel } from './clusterFilter.model';

describe('Multiselect Model', () => {

  let MultiselectModel: any, ClusterFilterModel: ClusterFilterModel, $state: StateService;

  beforeEach(mock.module(
    require('./multiselect.model')
  ));

  beforeEach(
    mock.inject((_MultiselectModel_: any, _$state_: StateService, _ClusterFilterModel_: ClusterFilterModel) => {
      MultiselectModel = _MultiselectModel_;
      ClusterFilterModel = _ClusterFilterModel_;
      $state = _$state_;
    })
  );

  describe('navigation management', () => {
    let result: any, currentStates: any[], currentParams: any;
    beforeEach(() => {
      ClusterFilterModel.asFilterModel.sortFilter.multiselect = true;
      result = null;
      currentStates = [];
      currentParams = {};

      spyOn($state, 'includes').and.callFake((substate: any) => currentStates.includes(substate));
      spyOn($state, 'go').and.callFake((newState: any) => result = newState);
      spyOnProperty($state, 'params', 'get').and.callFake(() => currentParams);
      spyOnProperty($state, '$current', 'get').and.callFake(() => {
        if (currentStates.length) {
          return { name: currentStates[currentStates.length - 1] };
        }
        return { name: '' };
      });
    });

    describe('syncNavigation', () => {
      describe('instance selection', () => {
        let instanceGroup: any;
        beforeEach(() => {
          instanceGroup = MultiselectModel.getOrCreateInstanceGroup(
            {name: 'a', account: 'prod', region: 'us-east-1', type: 'aws'});
        });

        it('navigates to multipleInstances child view when not already there and instances are selected', () => {
          instanceGroup.instanceIds.push('i-123');
          instanceGroup.instanceIds.push('i-124');
          currentStates.push('.clusters');
          MultiselectModel.syncNavigation();
          expect(result).toBe('.multipleInstances');
        });

        it('navigates to multipleInstances child view when not already there and an instance is selected', () => {
          instanceGroup.instanceIds.push('i-123');
          currentStates.push('.clusters');
          MultiselectModel.syncNavigation();
          expect(result).toBe('.multipleInstances');
        });

        it('navigates to multipleInstances sibling view when not already there and instances are selected', () => {
          instanceGroup.instanceIds.push('i-123');
          instanceGroup.instanceIds.push('i-124');
          currentStates.push('.clusters.instanceDetails');
          MultiselectModel.syncNavigation();
          expect(result).toBe('^.multipleInstances');
        });

        it('does not navigate when already in multipleInstances view', () => {
          instanceGroup.instanceIds.push('i-123');
          instanceGroup.instanceIds.push('i-124');
          currentStates = ['**.clusters.*', '**.multipleInstances'];
          MultiselectModel.syncNavigation();
          expect(result).toBe(null);
        });

        it('navigates away from multipleInstances view when no instances are selected', () => {
          currentStates = ['**.clusters.*', '**.multipleInstances'];
          MultiselectModel.syncNavigation();
          expect(result).toBe('^');
        });
      });

      describe('server group selection', () => {
        let serverGroup: any;
        beforeEach(() => {
          serverGroup = {name: 'a', account: 'prod', region: 'us-east-1', type: 'aws', category: 'serverGroup'};
        });

        it('navigates to multipleServerGroups child view when not already there and group is selected', () => {
          currentStates.push('.clusters');
          MultiselectModel.toggleServerGroup(serverGroup);
          expect(result).toBe('.multipleServerGroups');
        });

        it('navigates to multipleServerGroups sibling view when not already there and group is selected', () => {
          currentStates = ['**.clusters.*', '**.clusters.serverGroup'];
          MultiselectModel.toggleServerGroup(serverGroup);
          expect(result).toBe('^.multipleServerGroups');
        });

        it('does not navigate when already in multipleServerGroups view', () => {
          currentStates = ['**.clusters.*', '**.multipleServerGroups'];
          MultiselectModel.toggleServerGroup(serverGroup);
          expect(result).toBe(null);
        });

        it('navigates away from multipleServerGroups view when no server groups are selected', () => {
          currentStates = ['**.clusters.*', '**.multipleServerGroups'];
          MultiselectModel.syncNavigation();
          expect(result).toBe('^');
        });
      });
    });

    describe('toggleServerGroup', () => {
      let serverGroup: any;
      beforeEach(() => {
        serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws',
          category: 'serverGroup'
        };
      });

      it('navigates to details child view when multiselect is not enabled and not in clusters child view', () => {
        ClusterFilterModel.asFilterModel.sortFilter.multiselect = false;
        currentStates.push('.clusters');
        MultiselectModel.toggleServerGroup(serverGroup);
        expect(result).toBe('.serverGroup');
      });

      it('navigates to details sibling view when multiselect is not enabled and in clusters child view', () => {
        ClusterFilterModel.asFilterModel.sortFilter.multiselect = false;
        currentStates = ['**.clusters.*'];
        MultiselectModel.toggleServerGroup(serverGroup);
        expect(result).toBe('^.serverGroup');
      });

      it('deselects all instances when toggling', () => {
        const instanceGroup = MultiselectModel.getOrCreateInstanceGroup(serverGroup);
        instanceGroup.instanceIds.push('i-124');
        MultiselectModel.toggleServerGroup(serverGroup);
        expect(instanceGroup.instanceIds).toEqual([]);
      });

      it('toggles server group, creates model when added, always calls next', () => {
        expect(MultiselectModel.serverGroups.length).toBe(0);
        let nextCalls = 0;
        MultiselectModel.serverGroupsStream.subscribe(() => nextCalls++);

        MultiselectModel.toggleServerGroup(serverGroup);
        expect(nextCalls).toBe(1);
        expect(MultiselectModel.serverGroups.length).toBe(1);

        const model = MultiselectModel.serverGroups[0];
        expect(model.name).toBe('asg-v001');
        expect(model.account).toBe('prod');
        expect(model.region).toBe('us-east-1');
        expect(model.provider).toBe('aws');

        MultiselectModel.toggleServerGroup(serverGroup);
        expect(MultiselectModel.serverGroups.length).toBe(0);
        expect(nextCalls).toBe(2);
      });

      it('handles multiple server groups', () => {
        const otherServerGroup = {
          name: 'asg-v002',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws',
          category: 'serverGroup'
        };

        expect(MultiselectModel.serverGroups.length).toBe(0);
        MultiselectModel.toggleServerGroup(serverGroup);
        MultiselectModel.toggleServerGroup(otherServerGroup);
        expect(MultiselectModel.serverGroups.length).toBe(2);

        MultiselectModel.toggleServerGroup(serverGroup);
        expect(MultiselectModel.serverGroups.length).toBe(1);
        expect(MultiselectModel.serverGroups[0].name).toBe('asg-v002');
      });
    });

    describe('getOrCreateInstanceGroup', () => {
      let serverGroup: any, original: any;
      beforeEach(() => {
        serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws'
        };
        original = MultiselectModel.getOrCreateInstanceGroup(serverGroup);
      });

      it('reuses existing instance group', () => {
        const test = MultiselectModel.getOrCreateInstanceGroup(serverGroup);
        expect(test).toBe(original);
      });

      it('creates new instance group if name does not match', () => {
        serverGroup.name += 'a';
        const test = MultiselectModel.getOrCreateInstanceGroup(serverGroup);
        expect(test).not.toBe(original);
      });

      it('creates new instance group if region does not match', () => {
        serverGroup.region += 'a';
        const test = MultiselectModel.getOrCreateInstanceGroup(serverGroup);
        expect(test).not.toBe(original);
      });

      it('creates new instance group if account does not match', () => {
        serverGroup.account += 'a';
        const test = MultiselectModel.getOrCreateInstanceGroup(serverGroup);
        expect(test).not.toBe(original);
      });

      it('creates new instance group if type does not match', () => {
        serverGroup.type += 'a';
        const test = MultiselectModel.getOrCreateInstanceGroup(serverGroup);
        expect(test).not.toBe(original);
      });

      it('includes instance id if present in state params', () => {
        currentParams = { provider: 'aws', instanceId: 'i-123' };
        serverGroup.instances = [ { id: 'i-123', provider: 'aws' } ];
        serverGroup.name = 'asg-v002';
        const test = MultiselectModel.getOrCreateInstanceGroup(serverGroup);
        expect(test.instanceIds).toEqual(['i-123']);
      });
    });

    describe('toggleInstance', () => {
      let serverGroup: any, instanceGroup: any;
      beforeEach(() => {
        serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws'
        };

        instanceGroup = MultiselectModel.getOrCreateInstanceGroup(serverGroup);

        spyOn(MultiselectModel, 'syncNavigation');
      });

      it('adds instance id if not present', () => {
        MultiselectModel.toggleInstance(serverGroup, 'i-1234');

        expect(instanceGroup.instanceIds).toEqual(['i-1234']);
        expect(MultiselectModel.syncNavigation.calls.count()).toBe(1);
      });

      it('removes instance id if present and sets selectAll flag to false', () => {
        instanceGroup.instanceIds.push('i-1234');
        instanceGroup.selectAll = true;

        MultiselectModel.toggleInstance(serverGroup, 'i-1234');

        expect(instanceGroup.instanceIds).toEqual([]);
        expect(instanceGroup.selectAll).toBe(false);
        expect(MultiselectModel.syncNavigation.calls.count()).toBe(1);
      });

      it('clears server groups', () => {
        MultiselectModel.toggleServerGroup(serverGroup);
        expect(MultiselectModel.serverGroups.length).toBe(1);

        MultiselectModel.toggleInstance(serverGroup, 'i-1234');
        expect(MultiselectModel.serverGroups.length).toBe(0);
      });
    });

    describe('toggleSelectAll', () => {
      let serverGroup: any, instanceGroup: any;
      beforeEach(() => {
        serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws'
        };

        instanceGroup = MultiselectModel.getOrCreateInstanceGroup(serverGroup);

        spyOn(MultiselectModel, 'syncNavigation');
      });

      it('clears server groups', () => {
        MultiselectModel.toggleServerGroup(serverGroup);
        MultiselectModel.toggleSelectAll(serverGroup);
        expect(MultiselectModel.serverGroups.length).toBe(0);
      });

      it('sets selectAll flag to true and adds supplied instanceIds when selectAll is false', () => {
        const instanceIds = ['i-1234', 'i-2345'];
        MultiselectModel.toggleSelectAll(serverGroup, instanceIds);

        expect(instanceGroup.selectAll).toBe(true);
        expect(instanceGroup.instanceIds).toBe(instanceIds);
        expect(MultiselectModel.syncNavigation.calls.count()).toBe(1);
      });

      it('sets selectAll flag to false and clears supplied instanceIds when selectAll is true', () => {
        const instanceIds = ['i-1234', 'i-2345'];
        instanceGroup.selectAll = true;
        instanceGroup.instanceIds = instanceIds;
        MultiselectModel.toggleSelectAll(serverGroup, instanceIds);

        expect(instanceGroup.selectAll).toBe(false);
        expect(instanceGroup.instanceIds).toEqual([]);
        expect(MultiselectModel.syncNavigation.calls.count()).toBe(1);
      });
    });

    describe('instanceIsSelected', () => {
      it('returns true if instance is selected, false otherwise', () => {
        const serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws'
        };

        const instanceId = 'i-1234';

        const instanceGroup = MultiselectModel.getOrCreateInstanceGroup(serverGroup);
        instanceGroup.instanceIds.push(instanceId);

        expect(MultiselectModel.instanceIsSelected(serverGroup, instanceId)).toBe(true);
        expect(MultiselectModel.instanceIsSelected(serverGroup, instanceId + 'a')).toBe(false);
      });
    });

  });
});
