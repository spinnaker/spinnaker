'use strict';

describe('Multiselect Model', function () {

  var MultiselectModel, ClusterFilterModel, $state;

  beforeEach(window.module(
    require('./multiselect.model'),
    require('./clusterFilter.model')
  ));

  beforeEach(
    window.inject(function(_MultiselectModel_, _$state_, _ClusterFilterModel_) {
      MultiselectModel = _MultiselectModel_;
      ClusterFilterModel = _ClusterFilterModel_;
      $state = _$state_;
    })
  );

  describe('navigation management', function () {
    beforeEach(function() {
      ClusterFilterModel.sortFilter.multiselect = true;
      this.result = null;
      this.currentStates = [];
      spyOn($state, 'includes').and.callFake((substate) => this.currentStates.indexOf(substate) > -1);
      spyOn($state, 'go').and.callFake((newState) => this.result = newState);
      $state.params = {};
    });

    describe('syncNavigation', function () {
      describe('instance selection', function () {
        beforeEach(function() {
          this.instanceGroup = MultiselectModel.getOrCreateInstanceGroup(
            {name: 'a', account: 'prod', region: 'us-east-1', type: 'aws'});
        });

        it('navigates to multipleInstances child view when not already there and instances are selected', function () {
          this.instanceGroup.instanceIds.push('i-123');
          this.instanceGroup.instanceIds.push('i-124');
          $state.$current = { name: 'clusters' };
          MultiselectModel.syncNavigation();
          expect(this.result).toBe('.multipleInstances');
        });

        it('navigates to multipleInstances child view when not already there and an instance is selected', function () {
          this.instanceGroup.instanceIds.push('i-123');
          $state.$current = { name: 'clusters' };
          MultiselectModel.syncNavigation();
          expect(this.result).toBe('.multipleInstances');
        });

        it('navigates to multipleInstances sibling view when not already there and instances are selected', function () {
          this.instanceGroup.instanceIds.push('i-123');
          this.instanceGroup.instanceIds.push('i-124');
          $state.$current = { name: 'clusters.instanceDetails' };
          MultiselectModel.syncNavigation();
          expect(this.result).toBe('^.multipleInstances');
        });

        it('does not navigate when already in multipleInstances view', function () {
          this.instanceGroup.instanceIds.push('i-123');
          this.instanceGroup.instanceIds.push('i-124');
          this.currentStates = ['**.clusters.*', '**.multipleInstances'];
          MultiselectModel.syncNavigation();
          expect(this.result).toBe(null);
        });

        it('navigates away from multipleInstances view when no instances are selected', function () {
          this.currentStates = ['**.clusters.*', '**.multipleInstances'];
          MultiselectModel.syncNavigation();
          expect(this.result).toBe('^');
        });
      });

      describe('server group selection', function () {
        beforeEach(function() {
          this.serverGroup = {name: 'a', account: 'prod', region: 'us-east-1', type: 'aws', category: 'serverGroup'};
        });

        it('navigates to multipleServerGroups child view when not already there and group is selected', function () {
          $state.$current = { name: 'clusters' };
          MultiselectModel.toggleServerGroup(this.serverGroup);
          expect(this.result).toBe('.multipleServerGroups');
        });

        it('navigates to multipleServerGroups sibling view when not already there and group is selected', function () {
          this.currentStates = ['**.clusters.*', '**.clusters.serverGroup'];
          MultiselectModel.toggleServerGroup(this.serverGroup);
          expect(this.result).toBe('^.multipleServerGroups');
        });

        it('does not navigate when already in multipleServerGroups view', function () {
          this.currentStates = ['**.clusters.*', '**.multipleServerGroups'];
          MultiselectModel.toggleServerGroup(this.serverGroup);
          expect(this.result).toBe(null);
        });

        it('navigates away from multipleServerGroups view when no server groups are selected', function () {
          this.currentStates = ['**.clusters.*', '**.multipleServerGroups'];
          MultiselectModel.syncNavigation();
          expect(this.result).toBe('^');
        });
      });
    });

    describe('toggleServerGroup', function () {
      beforeEach(function () {
        this.serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws',
          category: 'serverGroup'
        };
      });

      it('navigates to details child view when multiselect is not enabled and not in clusters child view', function () {
        ClusterFilterModel.sortFilter.multiselect = false;
        $state.$current = { name: 'clusters' };
        MultiselectModel.toggleServerGroup(this.serverGroup);
        expect(this.result).toBe('.serverGroup');
      });

      it('navigates to details sibling view when multiselect is not enabled and in clusters child view', function () {
        ClusterFilterModel.sortFilter.multiselect = false;
        this.currentStates = ['**.clusters.*'];
        MultiselectModel.toggleServerGroup(this.serverGroup);
        expect(this.result).toBe('^.serverGroup');
      });

      it('deselects all instances when toggling', function () {
        let instanceGroup = MultiselectModel.getOrCreateInstanceGroup(this.serverGroup);
        instanceGroup.instanceIds.push('i-124');
        MultiselectModel.toggleServerGroup(this.serverGroup);
        expect(instanceGroup.instanceIds).toEqual([]);
      });

      it('toggles server group, creates model when added, always calls next', function () {
        expect(MultiselectModel.serverGroups.length).toBe(0);
        let nextCalls = 0;
        MultiselectModel.serverGroupsStream.subscribe(() => nextCalls++);

        MultiselectModel.toggleServerGroup(this.serverGroup);
        expect(nextCalls).toBe(1);
        expect(MultiselectModel.serverGroups.length).toBe(1);
        let model = MultiselectModel.serverGroups[0];
        expect(model.name).toBe('asg-v001');
        expect(model.account).toBe('prod');
        expect(model.region).toBe('us-east-1');
        expect(model.provider).toBe('aws');

        MultiselectModel.toggleServerGroup(this.serverGroup);
        expect(MultiselectModel.serverGroups.length).toBe(0);
        expect(nextCalls).toBe(2);
      });

      it('handles multiple server groups', function () {
        let otherServerGroup = {
          name: 'asg-v002',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws',
          category: 'serverGroup'
        };

        expect(MultiselectModel.serverGroups.length).toBe(0);
        MultiselectModel.toggleServerGroup(this.serverGroup);
        MultiselectModel.toggleServerGroup(otherServerGroup);
        expect(MultiselectModel.serverGroups.length).toBe(2);

        MultiselectModel.toggleServerGroup(this.serverGroup);
        expect(MultiselectModel.serverGroups.length).toBe(1);
        expect(MultiselectModel.serverGroups[0].name).toBe('asg-v002');
      });
    });

    describe('getOrCreateInstanceGroup', function () {
      beforeEach(function () {
        this.serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws'
        };
        this.original = MultiselectModel.getOrCreateInstanceGroup(this.serverGroup);
      });

      it('reuses existing instance group', function () {
        let test = MultiselectModel.getOrCreateInstanceGroup(this.serverGroup);
        expect(test).toBe(this.original);
      });

      it('creates new instance group if name does not match', function () {
        this.serverGroup.name += 'a';
        let test = MultiselectModel.getOrCreateInstanceGroup(this.serverGroup);
        expect(test).not.toBe(this.original);
      });

      it('creates new instance group if region does not match', function () {
        this.serverGroup.region += 'a';
        let test = MultiselectModel.getOrCreateInstanceGroup(this.serverGroup);
        expect(test).not.toBe(this.original);
      });

      it('creates new instance group if account does not match', function () {
        this.serverGroup.account += 'a';
        let test = MultiselectModel.getOrCreateInstanceGroup(this.serverGroup);
        expect(test).not.toBe(this.original);
      });

      it('creates new instance group if type does not match', function () {
        this.serverGroup.type += 'a';
        let test = MultiselectModel.getOrCreateInstanceGroup(this.serverGroup);
        expect(test).not.toBe(this.original);
      });

      it('includes instance id if present in state params', function () {
        $state.params = { provider: 'aws', instanceId: 'i-123' };
        this.serverGroup.instances = [ { id: 'i-123', provider: 'aws' } ];
        this.serverGroup.name = 'asg-v002';
        let test = MultiselectModel.getOrCreateInstanceGroup(this.serverGroup);
        expect(test.instanceIds).toEqual(['i-123']);
      });
    });

    describe('toggleInstance', function () {
      beforeEach(function () {
        this.serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws'
        };

        this.instanceGroup = MultiselectModel.getOrCreateInstanceGroup(this.serverGroup);

        spyOn(MultiselectModel, 'syncNavigation');
      });

      it('adds instance id if not present', function () {
        MultiselectModel.toggleInstance(this.serverGroup, 'i-1234');

        expect(this.instanceGroup.instanceIds).toEqual(['i-1234']);
        expect(MultiselectModel.syncNavigation.calls.count()).toBe(1);
      });

      it('removes instance id if present and sets selectAll flag to false', function () {
        this.instanceGroup.instanceIds.push('i-1234');
        this.instanceGroup.selectAll = true;

        MultiselectModel.toggleInstance(this.serverGroup, 'i-1234');

        expect(this.instanceGroup.instanceIds).toEqual([]);
        expect(this.instanceGroup.selectAll).toBe(false);
        expect(MultiselectModel.syncNavigation.calls.count()).toBe(1);
      });

      it('clears server groups', function () {
        MultiselectModel.toggleServerGroup(this.serverGroup);
        expect(MultiselectModel.serverGroups.length).toBe(1);

        MultiselectModel.toggleInstance(this.serverGroup, 'i-1234');
        expect(MultiselectModel.serverGroups.length).toBe(0);
      });
    });

    describe('toggleSelectAll', function () {
      beforeEach(function () {
        this.serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws'
        };

        this.instanceGroup = MultiselectModel.getOrCreateInstanceGroup(this.serverGroup);

        spyOn(MultiselectModel, 'syncNavigation');
      });

      it('clears server groups', function () {
        MultiselectModel.toggleServerGroup(this.serverGroup);
        MultiselectModel.toggleSelectAll(this.serverGroup);
        expect(MultiselectModel.serverGroups.length).toBe(0);
      });

      it('sets selectAll flag to true and adds supplied instanceIds when selectAll is false', function () {
        let instanceIds = ['i-1234', 'i-2345'];
        MultiselectModel.toggleSelectAll(this.serverGroup, instanceIds);

        expect(this.instanceGroup.selectAll).toBe(true);
        expect(this.instanceGroup.instanceIds).toBe(instanceIds);
        expect(MultiselectModel.syncNavigation.calls.count()).toBe(1);
      });

      it('sets selectAll flag to false and clears supplied instanceIds when selectAll is true', function () {
        let instanceIds = ['i-1234', 'i-2345'];
        this.instanceGroup.selectAll = true;
        this.instanceGroup.instanceIds = instanceIds;
        MultiselectModel.toggleSelectAll(this.serverGroup, instanceIds);

        expect(this.instanceGroup.selectAll).toBe(false);
        expect(this.instanceGroup.instanceIds).toEqual([]);
        expect(MultiselectModel.syncNavigation.calls.count()).toBe(1);
      });
    });

    describe('instanceIsSelected', function () {
      it('returns true if instance is selected, false otherwise', function () {
        let serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws'
        };

        let instanceId = 'i-1234';

        let instanceGroup = MultiselectModel.getOrCreateInstanceGroup(serverGroup);
        instanceGroup.instanceIds.push(instanceId);

        expect(MultiselectModel.instanceIsSelected(serverGroup, instanceId)).toBe(true);
        expect(MultiselectModel.instanceIsSelected(serverGroup, instanceId + 'a')).toBe(false);
      });
    });

  });
});
