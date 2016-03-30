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

  describe('state management', function () {
    beforeEach(function() {
      this.result = null;
      this.currentStates = [];
      spyOn($state, 'includes').and.callFake((substate) => this.currentStates.indexOf(substate) > -1);
      spyOn($state, 'go').and.callFake((newState) => this.result = newState);
      $state.params = {};
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
