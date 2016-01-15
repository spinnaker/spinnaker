'use strict';


describe('Cluster Filter Model', function () {

  var ClusterFilterModel, $state;

  beforeEach(window.module(
    require('./clusterFilter.model')
  ));

  beforeEach(
    window.inject(function(_ClusterFilterModel_, _$state_) {
      ClusterFilterModel = _ClusterFilterModel_;
      $state = _$state_;
    })
  );

  describe('multiple instance management', function () {
    beforeEach(function() {
      this.result = null;
      this.currentStates = [];
      spyOn($state, 'includes').and.callFake((substate) => this.currentStates.indexOf(substate) > -1);
      spyOn($state, 'go').and.callFake((newState) => this.result = newState);
    });

    describe('syncNavigation', function () {
      beforeEach(function() {
        this.instanceGroup = ClusterFilterModel.getOrCreateMultiselectInstanceGroup(
          {name: 'a', account: 'prod', region: 'us-east-1', type: 'aws'});
      });

      it('navigates to multipleInstances child view when not already there and instances are selected', function () {
        this.instanceGroup.instanceIds.push('i-123');
        this.currentStates = ['**.clusters.*'];
        ClusterFilterModel.syncNavigation();
        expect(this.result).toBe('^.multipleInstances');
      });

      it('navigates to multipleInstances sibling view when not already there and instances are selected', function () {
        this.instanceGroup.instanceIds.push('i-123');
        this.currentStates = ['**.clusters.instanceDetails'];
        ClusterFilterModel.syncNavigation();
        expect(this.result).toBe('.multipleInstances');
      });

      it('does not navigate when already in multipleInstances view', function () {
        this.instanceGroup.instanceIds.push('i-123');
        this.currentStates = ['**.multipleInstances'];
        ClusterFilterModel.syncNavigation();
        expect(this.result).toBe(null);
      });

      it('navigates away from multipleInstances view when no instances are selected', function () {
        this.currentStates = ['**.multipleInstances'];
        ClusterFilterModel.syncNavigation();
        expect(this.result).toBe('^');
      });
    });

    describe('getOrCreateMultiselectInstanceGroup', function () {
      beforeEach(function () {
        this.serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws'
        };
        this.original = ClusterFilterModel.getOrCreateMultiselectInstanceGroup(this.serverGroup);
      });

      it('reuses existing instance group', function () {
        let test = ClusterFilterModel.getOrCreateMultiselectInstanceGroup(this.serverGroup);
        expect(test).toBe(this.original);
      });

      it('creates new instance group if name does not match', function () {
        this.serverGroup.name += 'a';
        let test = ClusterFilterModel.getOrCreateMultiselectInstanceGroup(this.serverGroup);
        expect(test).not.toBe(this.original);
      });

      it('creates new instance group if region does not match', function () {
        this.serverGroup.region += 'a';
        let test = ClusterFilterModel.getOrCreateMultiselectInstanceGroup(this.serverGroup);
        expect(test).not.toBe(this.original);
      });

      it('creates new instance group if account does not match', function () {
        this.serverGroup.account += 'a';
        let test = ClusterFilterModel.getOrCreateMultiselectInstanceGroup(this.serverGroup);
        expect(test).not.toBe(this.original);
      });

      it('creates new instance group if type does not match', function () {
        this.serverGroup.type += 'a';
        let test = ClusterFilterModel.getOrCreateMultiselectInstanceGroup(this.serverGroup);
        expect(test).not.toBe(this.original);
      });
    });

    describe('toggleMultiselectInstance', function () {
      beforeEach(function () {
        this.serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws'
        };

        this.instanceGroup = ClusterFilterModel.getOrCreateMultiselectInstanceGroup(this.serverGroup);

        spyOn(ClusterFilterModel, 'syncNavigation');
      });

      it('adds instance id if not present', function () {
        ClusterFilterModel.toggleMultiselectInstance(this.serverGroup, 'i-1234');

        expect(this.instanceGroup.instanceIds).toEqual(['i-1234']);
        expect(ClusterFilterModel.syncNavigation.calls.count()).toBe(1);
      });

      it('removes instance id if present and sets selectAll flag to false', function () {
        this.instanceGroup.instanceIds.push('i-1234');
        this.instanceGroup.selectAll = true;

        ClusterFilterModel.toggleMultiselectInstance(this.serverGroup, 'i-1234');

        expect(this.instanceGroup.instanceIds).toEqual([]);
        expect(this.instanceGroup.selectAll).toBe(false);
        expect(ClusterFilterModel.syncNavigation.calls.count()).toBe(1);
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

        this.instanceGroup = ClusterFilterModel.getOrCreateMultiselectInstanceGroup(this.serverGroup);

        spyOn(ClusterFilterModel, 'syncNavigation');
      });

      it('sets selectAll flag to true and adds supplied instanceIds when selectAll is false', function () {
        let instanceIds = ['i-1234', 'i-2345'];
        ClusterFilterModel.toggleSelectAll(this.serverGroup, instanceIds);

        expect(this.instanceGroup.selectAll).toBe(true);
        expect(this.instanceGroup.instanceIds).toBe(instanceIds);
        expect(ClusterFilterModel.syncNavigation.calls.count()).toBe(1);
      });

      it('sets selectAll flag to false and clears supplied instanceIds when selectAll is true', function () {
        let instanceIds = ['i-1234', 'i-2345'];
        this.instanceGroup.selectAll = true;
        this.instanceGroup.instanceIds = instanceIds;
        ClusterFilterModel.toggleSelectAll(this.serverGroup, instanceIds);

        expect(this.instanceGroup.selectAll).toBe(false);
        expect(this.instanceGroup.instanceIds).toEqual([]);
        expect(ClusterFilterModel.syncNavigation.calls.count()).toBe(1);
      });
    });

    describe('instanceIsMultiselected', function () {
      it('returns true if instance is selected, false otherwise', function () {
        let serverGroup = {
          name: 'asg-v001',
          account: 'prod',
          region: 'us-east-1',
          type: 'aws'
        };

        let instanceId = 'i-1234';

        let instanceGroup = ClusterFilterModel.getOrCreateMultiselectInstanceGroup(serverGroup);
        instanceGroup.instanceIds.push(instanceId);

        expect(ClusterFilterModel.instanceIsMultiselected(serverGroup, instanceId)).toBe(true);
        expect(ClusterFilterModel.instanceIsMultiselected(serverGroup, instanceId + 'a')).toBe(false);
      });
    });

    describe('state change start event', function () {
      it('clears multiselectInstanceGroups when navigating away from clusters view', function () {
        let oldState = { name: 'home.applications.application.insight.clusters' },
            newState = { name: 'home.applications.application.insight.loadBalancers' },
            oldParams = { application: 'a' },
            newParams = { application: 'a' };
        ClusterFilterModel.multiselectInstanceGroups = [ 'it does not matter, we are just verifying the array is cleared'];

        ClusterFilterModel.handleStateChangeStart(null, newState, newParams, oldState, oldParams);
        expect(ClusterFilterModel.multiselectInstanceGroups).toEqual([]);
      });

      it('clears multiselectInstanceGroups when navigating to a different application', function () {
        let oldState = { name: 'home.applications.application.insight.clusters' },
            newState = { name: 'home.applications.application.insight.clusters' },
            oldParams = { application: 'a' },
            newParams = { application: 'b' };
        ClusterFilterModel.multiselectInstanceGroups = [ 'it does not matter, we are just verifying the array is cleared'];

        ClusterFilterModel.handleStateChangeStart(null, newState, newParams, oldState, oldParams);
        expect(ClusterFilterModel.multiselectInstanceGroups).toEqual([]);
      });

      it('preserves multiselectInstanceGroups when navigating to multipleInstances', function () {
        let oldState = { name: 'home.applications.application.insight.clusters' },
            newState = { name: 'clusters.multipleInstances' },
            oldParams = { application: 'a' },
            newParams = { application: 'a' },
            oldGroups = [ 'a', 'b', 'c', 'just testing it does not get cleared'];

        ClusterFilterModel.multiselectInstanceGroups = oldGroups;

        ClusterFilterModel.handleStateChangeStart(null, newState, newParams, oldState, oldParams);
        expect(ClusterFilterModel.multiselectInstanceGroups.length).toBe(4);
      });
    });

    describe('removing checked AZs if region is not selected', () => {
      it('should remove the us-west-1a AZ if the eu-west-1 region is selected and us-west-1 region is not', function () {
        ClusterFilterModel.sortFilter.availabilityZone = {'us-west-1a': true};
        ClusterFilterModel.sortFilter.region = {'eu-west-1': true};
        ClusterFilterModel.removeCheckedAvailabilityZoneIfRegionIsNotChecked();

        expect(ClusterFilterModel.getSelectedAvailabilityZones()).toEqual([]);
      });

      it('should keep the us-west-1a AZ if no region is selected', function () {
        ClusterFilterModel.sortFilter.availabilityZone = {'us-west-1a': true};
        ClusterFilterModel.sortFilter.region = {};
        ClusterFilterModel.removeCheckedAvailabilityZoneIfRegionIsNotChecked();

        expect(ClusterFilterModel.getSelectedAvailabilityZones()).toEqual(['us-west-1a']);
      });

      it('should keep the us-west-1a AZ if us-west-1 and some other region are selected', function () {
        ClusterFilterModel.sortFilter.availabilityZone = {'us-west-1a': true};
        ClusterFilterModel.sortFilter.region = {'us-west-1' : true, 'eu-east-2' : true};
        ClusterFilterModel.removeCheckedAvailabilityZoneIfRegionIsNotChecked();

        expect(ClusterFilterModel.getSelectedAvailabilityZones()).toEqual(['us-west-1a']);
      });

      it('should remove the us-west-1a AZ if us-west-1 & eu-east-2 are selected and then the us-west-1 region is unchecked', function () {
        ClusterFilterModel.sortFilter.availabilityZone = {'us-west-1a': true};
        ClusterFilterModel.sortFilter.region = {'us-west-1' : true, 'eu-east-2' : true};

        ClusterFilterModel.removeCheckedAvailabilityZoneIfRegionIsNotChecked();
        expect(ClusterFilterModel.getSelectedAvailabilityZones()).toEqual(['us-west-1a']);

        ClusterFilterModel.sortFilter.region = {'us-west-1' : false, 'eu-east-2' : true};

        ClusterFilterModel.removeCheckedAvailabilityZoneIfRegionIsNotChecked();
        expect(ClusterFilterModel.getSelectedAvailabilityZones()).toEqual([]);
      });
    });

  });
});
