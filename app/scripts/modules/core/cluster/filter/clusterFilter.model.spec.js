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

    let regionsKeyedByAccount;
    beforeEach(function() {
      regionsKeyedByAccount = {
        'my-aws-account': ['us-west-2', 'us-west-1', 'eu-east-2'],
        'my-google-account': ['us-central1', 'asia-east1', 'europe-west1']
      };
    });

    describe('removing checked AZs if region is not selected', () => {
      it('should remove the us-west-1a AZ if the eu-west-1 region is selected and us-west-1 region is not', function () {
        ClusterFilterModel.sortFilter.availabilityZone = {'us-west-1a': true};
        ClusterFilterModel.sortFilter.region = {'eu-west-1': true};

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        expect(ClusterFilterModel.getSelectedAvailabilityZones()).toEqual([]);
      });

      it('should keep the us-west-1a AZ if no region is selected', function () {
        ClusterFilterModel.sortFilter.availabilityZone = {'us-west-1a': true};
        ClusterFilterModel.sortFilter.region = {};

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        expect(ClusterFilterModel.getSelectedAvailabilityZones()).toEqual(['us-west-1a']);
      });

      it('should keep the us-west-1a AZ if us-west-1 and some other region are selected', function () {
        ClusterFilterModel.sortFilter.availabilityZone = {'us-west-1a': true};
        ClusterFilterModel.sortFilter.region = {'us-west-1' : true, 'eu-east-2' : true};

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        expect(ClusterFilterModel.getSelectedAvailabilityZones()).toEqual(['us-west-1a']);
      });

      it('should remove the us-west-1a AZ if us-west-1 & eu-east-2 are selected and then the us-west-1 region is unchecked', function () {
        ClusterFilterModel.sortFilter.availabilityZone = {'us-west-1a': true};
        ClusterFilterModel.sortFilter.region = {'us-west-1' : true, 'eu-east-2' : true};

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        ClusterFilterModel.sortFilter.region = {'us-west-1' : false, 'eu-east-2' : true};

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        expect(ClusterFilterModel.getSelectedAvailabilityZones()).toEqual([]);
      });
    });

    describe('removing checked regions if corresponding accounts are not selected', function () {
      it('should remove us-central1 region if only my-aws-account is selected', function () {
        ClusterFilterModel.sortFilter.account = { 'my-aws-account' : true };
        ClusterFilterModel.sortFilter.region = { 'us-central1' : true };

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        expect(ClusterFilterModel.getSelectedRegions()).toEqual([]);
      });

      it('should keep us-central1 region if my-google-account is selected', function () {
        ClusterFilterModel.sortFilter.account = { 'my-google-account' : true };
        ClusterFilterModel.sortFilter.region = { 'us-central1' : true };

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        expect(ClusterFilterModel.getSelectedRegions()).toEqual(['us-central1']);
      });

      it('should keep asia-east1 region if my-google-account and some other account are selected', function () {
        ClusterFilterModel.sortFilter.account = { 'my-google-account' : true, 'my-aws-account' : true };
        ClusterFilterModel.sortFilter.region = { 'asia-east1' : true };

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        expect(ClusterFilterModel.getSelectedRegions()).toEqual(['asia-east1']);
      });

      it('should remove us-central1 and asia-east1 regions if my-google-account and some other account are selected and then my-google-account is unselected', function () {
        ClusterFilterModel.sortFilter.account = { 'my-google-account' : true, 'my-aws-account' : true };
        ClusterFilterModel.sortFilter.region = { 'us-central1' : true, 'asia-east1' : true };

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        expect(ClusterFilterModel.getSelectedRegions()).toEqual(['us-central1', 'asia-east1']);

        ClusterFilterModel.sortFilter.account = { 'my-google-account' : false, 'my-aws-account' : true };

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        expect(ClusterFilterModel.getSelectedRegions()).toEqual([]);
      });

    });

    describe('removing checked AZs if corresponding accounts are not selected', function () {
      it('should remove us-central1-f AZ if only my-aws-account is selected', function () {
        ClusterFilterModel.sortFilter.account = { 'my-aws-account' : true };
        ClusterFilterModel.sortFilter.availabilityZone = { 'us-central1-f': true };

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        expect(ClusterFilterModel.getSelectedAvailabilityZones()).toEqual([]);
      });

      it('should keep us-central1-f AZ if my-google-account is selected', function () {
        ClusterFilterModel.sortFilter.account = { 'my-google-account' : true };
        ClusterFilterModel.sortFilter.availabilityZone = { 'us-central1-f': true };

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        expect(ClusterFilterModel.getSelectedAvailabilityZones()).toEqual(['us-central1-f']);
      });

      it('should keep asia-east1-b AZ if my-google-account and some other account are selected', function () {
        ClusterFilterModel.sortFilter.account = { 'my-google-account' : true, 'my-aws-account' : true };
        ClusterFilterModel.sortFilter.availabilityZone = { 'asia-east1-b': true };

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        expect(ClusterFilterModel.getSelectedAvailabilityZones()).toEqual(['asia-east1-b']);
      });

      it('should remove us-central1-f AZ if my-google-account and some other account are selected and then my-google-account is unselected', function () {
        ClusterFilterModel.sortFilter.account = { 'my-google-account' : true, 'my-aws-account' : true };
        ClusterFilterModel.sortFilter.availabilityZone = { 'us-central1-f': true };

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        expect(ClusterFilterModel.getSelectedAvailabilityZones()).toEqual(['us-central1-f']);

        ClusterFilterModel.sortFilter.account = { 'my-google-account' : false, 'my-aws-account' : true };

        ClusterFilterModel.reconcileDependentFilters(regionsKeyedByAccount);

        expect(ClusterFilterModel.getSelectedAvailabilityZones()).toEqual([]);
      });
    });
  });
});
