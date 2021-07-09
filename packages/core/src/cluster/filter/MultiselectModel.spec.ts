import { ReactInjector } from '../../reactShims';
import * as State from '../../state';
const { ClusterState } = State;

import { MultiselectModel } from './MultiselectModel';

describe('Multiselect Model', () => {
  let multiselectModel: MultiselectModel;
  beforeEach(() => (multiselectModel = new MultiselectModel()));

  describe('navigation management', () => {
    let result: any, currentStates: any[], currentParams: any;
    beforeEach(() => {
      ClusterState.filterModel.asFilterModel.sortFilter.multiselect = true;
      result = null;
      currentStates = [];
      currentParams = {};

      spyOn(ReactInjector.$state, 'includes').and.callFake((substate: any) => currentStates.includes(substate));
      spyOn(ReactInjector.$state, 'go').and.callFake((newState: any) => (result = newState));
      spyOnProperty(ReactInjector.$state, 'params', 'get').and.callFake(() => currentParams);
      spyOnProperty(ReactInjector.$state, '$current', 'get').and.callFake(() => {
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
          instanceGroup = multiselectModel.getOrCreateInstanceGroup({
            name: 'a',
            account: 'prod',
            region: 'us-east-1',
            type: 'aws',
          } as any);
        });

        it('navigates to multipleInstances child view when not already there and instances are selected', () => {
          instanceGroup.instanceIds.push('i-123');
          instanceGroup.instanceIds.push('i-124');
          currentStates.push('.clusters');
          multiselectModel.syncNavigation();
          expect(result).toBe('.multipleInstances');
        });

        it('navigates to multipleInstances child view when not already there and an instance is selected', () => {
          instanceGroup.instanceIds.push('i-123');
          currentStates.push('.clusters');
          multiselectModel.syncNavigation();
          expect(result).toBe('.multipleInstances');
        });

        it('navigates to multipleInstances sibling view when not already there and instances are selected', () => {
          instanceGroup.instanceIds.push('i-123');
          instanceGroup.instanceIds.push('i-124');
          currentStates.push('.clusters.instanceDetails');
          multiselectModel.syncNavigation();
          expect(result).toBe('^.multipleInstances');
        });

        it('does not navigate when already in multipleInstances view', () => {
          currentParams.multiselect = true;
          instanceGroup.instanceIds.push('i-123');
          instanceGroup.instanceIds.push('i-124');
          currentStates = ['**.clusters.*', '**.multipleInstances'];
          multiselectModel.syncNavigation();
          expect(result).toBe(null);
        });

        it('navigates away from multipleInstances view when no instances are selected', () => {
          currentStates = ['**.clusters.*', '**.multipleInstances'];
          multiselectModel.syncNavigation();
          expect(result).toBe('^');
        });
      });

      describe('server group selection', () => {
        let serverGroup: any;
        beforeEach(() => {
          serverGroup = { name: 'a', account: 'prod', region: 'us-east-1', type: 'aws', category: 'serverGroup' };
        });

        it('navigates to multipleServerGroups child view when not already there and group is selected', () => {
          currentStates.push('.clusters');
          multiselectModel.toggleServerGroup(serverGroup);
          expect(result).toBe('.multipleServerGroups');
        });

        it('navigates to multipleServerGroups sibling view when not already there and group is selected', () => {
          currentStates = ['**.clusters.*', '**.clusters.serverGroup'];
          multiselectModel.toggleServerGroup(serverGroup);
          expect(result).toBe('^.multipleServerGroups');
        });

        it('does not navigate when already in multipleServerGroups view', () => {
          currentParams.multiselect = true;
          currentStates = ['**.clusters.*', '**.multipleServerGroups'];
          multiselectModel.toggleServerGroup(serverGroup);
          expect(result).toBe(null);
        });

        it('navigates away from multipleServerGroups view when no server groups are selected', () => {
          currentStates = ['**.clusters.*', '**.multipleServerGroups'];
          multiselectModel.syncNavigation();
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
          category: 'serverGroup',
        };
      });

      it('navigates to details child view when multiselect is not enabled and not in clusters child view', () => {
        ClusterState.filterModel.asFilterModel.sortFilter.multiselect = false;
        currentStates.push('.clusters');
        multiselectModel.toggleServerGroup(serverGroup);
        expect(result).toBe('.serverGroup');
      });

      it('navigates to details sibling view when multiselect is not enabled and in clusters child view', () => {
        ClusterState.filterModel.asFilterModel.sortFilter.multiselect = false;
        currentStates = ['**.clusters.*'];
        multiselectModel.toggleServerGroup(serverGroup);
        expect(result).toBe('^.serverGroup');
      });

      it('deselects all instances when toggling', () => {
        const instanceGroup = multiselectModel.getOrCreateInstanceGroup(serverGroup);
        instanceGroup.instanceIds.push('i-124');
        multiselectModel.toggleServerGroup(serverGroup);
        expect(instanceGroup.instanceIds).toEqual([]);
      });

      it('toggles server group, creates model when added, always calls next', () => {
        expect(multiselectModel.serverGroups.length).toBe(0);
        let nextCalls = 0;
        multiselectModel.serverGroupsStream.subscribe(() => nextCalls++);

        multiselectModel.toggleServerGroup(serverGroup);
        expect(nextCalls).toBe(1);
        expect(multiselectModel.serverGroups.length).toBe(1);

        const model = multiselectModel.serverGroups[0];
        expect(model.name).toBe('asg-v001');
        expect(model.account).toBe('prod');
        expect(model.region).toBe('us-east-1');
        expect(model.provider).toBe('aws');

        multiselectModel.toggleServerGroup(serverGroup);
        expect(multiselectModel.serverGroups.length).toBe(0);
        expect(nextCalls).toBe(2);
      });

      it('handles multiple server groups', () => {
        const otherServerGroup = {
          name: 'asg-v002',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws',
          category: 'serverGroup',
        } as any;

        expect(multiselectModel.serverGroups.length).toBe(0);
        multiselectModel.toggleServerGroup(serverGroup);
        multiselectModel.toggleServerGroup(otherServerGroup);
        expect(multiselectModel.serverGroups.length).toBe(2);

        multiselectModel.toggleServerGroup(serverGroup);
        expect(multiselectModel.serverGroups.length).toBe(1);
        expect(multiselectModel.serverGroups[0].name).toBe('asg-v002');
      });
    });

    describe('getOrCreateInstanceGroup', () => {
      let serverGroup: any, original: any;
      beforeEach(() => {
        serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws',
        };
        original = multiselectModel.getOrCreateInstanceGroup(serverGroup);
      });

      it('reuses existing instance group', () => {
        const test = multiselectModel.getOrCreateInstanceGroup(serverGroup);
        expect(test).toBe(original);
      });

      it('creates new instance group if name does not match', () => {
        serverGroup.name += 'a';
        const test = multiselectModel.getOrCreateInstanceGroup(serverGroup);
        expect(test).not.toBe(original);
      });

      it('creates new instance group if region does not match', () => {
        serverGroup.region += 'a';
        const test = multiselectModel.getOrCreateInstanceGroup(serverGroup);
        expect(test).not.toBe(original);
      });

      it('creates new instance group if account does not match', () => {
        serverGroup.account += 'a';
        const test = multiselectModel.getOrCreateInstanceGroup(serverGroup);
        expect(test).not.toBe(original);
      });

      it('creates new instance group if type does not match', () => {
        serverGroup.type += 'a';
        const test = multiselectModel.getOrCreateInstanceGroup(serverGroup);
        expect(test).not.toBe(original);
      });

      it('includes instance id if present in state params', () => {
        currentParams = { provider: 'aws', instanceId: 'i-123' };
        serverGroup.instances = [{ id: 'i-123', provider: 'aws' }];
        serverGroup.name = 'asg-v002';
        const test = multiselectModel.getOrCreateInstanceGroup(serverGroup);
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
          type: 'aws',
        };

        instanceGroup = multiselectModel.getOrCreateInstanceGroup(serverGroup);

        spyOn(multiselectModel, 'syncNavigation');
      });

      it('adds instance id if not present', () => {
        multiselectModel.toggleInstance(serverGroup, 'i-1234');

        expect(instanceGroup.instanceIds).toEqual(['i-1234']);
        expect((multiselectModel.syncNavigation as any).calls.count()).toBe(1);
      });

      it('removes instance id if present and sets selectAll flag to false', () => {
        instanceGroup.instanceIds.push('i-1234');
        instanceGroup.selectAll = true;

        multiselectModel.toggleInstance(serverGroup, 'i-1234');

        expect(instanceGroup.instanceIds).toEqual([]);
        expect(instanceGroup.selectAll).toBe(false);
        expect((multiselectModel.syncNavigation as any).calls.count()).toBe(1);
      });

      it('clears server groups', () => {
        multiselectModel.toggleServerGroup(serverGroup);
        expect(multiselectModel.serverGroups.length).toBe(1);

        multiselectModel.toggleInstance(serverGroup, 'i-1234');
        expect(multiselectModel.serverGroups.length).toBe(0);
      });
    });

    describe('toggleSelectAll', () => {
      let serverGroup: any, instanceGroup: any;
      beforeEach(() => {
        serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws',
        };

        instanceGroup = multiselectModel.getOrCreateInstanceGroup(serverGroup);

        spyOn(multiselectModel, 'syncNavigation');
      });

      it('clears server groups', () => {
        multiselectModel.toggleServerGroup(serverGroup);
        multiselectModel.toggleSelectAll(serverGroup);
        expect(multiselectModel.serverGroups.length).toBe(0);
      });

      it('sets selectAll flag to true and adds supplied instanceIds when selectAll is false', () => {
        const instanceIds = ['i-1234', 'i-2345'];
        multiselectModel.toggleSelectAll(serverGroup, instanceIds);

        expect(instanceGroup.selectAll).toBe(true);
        expect(instanceGroup.instanceIds).toBe(instanceIds);
        expect((multiselectModel.syncNavigation as any).calls.count()).toBe(1);
      });

      it('sets selectAll flag to false and clears supplied instanceIds when selectAll is true', () => {
        const instanceIds = ['i-1234', 'i-2345'];
        instanceGroup.selectAll = true;
        instanceGroup.instanceIds = instanceIds;
        multiselectModel.toggleSelectAll(serverGroup, instanceIds);

        expect(instanceGroup.selectAll).toBe(false);
        expect(instanceGroup.instanceIds).toEqual([]);
        expect((multiselectModel.syncNavigation as any).calls.count()).toBe(1);
      });
    });

    describe('instanceIsSelected', () => {
      it('returns true if instance is selected, false otherwise', () => {
        const serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws',
        } as any;

        const instanceId = 'i-1234';

        const instanceGroup = multiselectModel.getOrCreateInstanceGroup(serverGroup);
        instanceGroup.instanceIds.push(instanceId);

        expect(multiselectModel.instanceIsSelected(serverGroup, instanceId)).toBe(true);
        expect(multiselectModel.instanceIsSelected(serverGroup, instanceId + 'a')).toBe(false);
      });
    });
  });
});
