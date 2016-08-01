'use strict';

describe('ClusterFilter Controller', function () {
  let controller;
  let scope;

  beforeEach(
    window.module(
      require('./clusterFilter.controller')
    )
  );

  beforeEach(
    window.inject(($controller, $rootScope) => {
      scope = $rootScope.$new();
      controller = $controller('ClusterFilterCtrl', {
        $scope: scope,
        app: {
          serverGroups: { data: [], loaded: true, onRefresh: angular.noop }
        }
      });
    })
  );

  describe('getAvailabilityZoneHeadings', function () {
    describe('where all regions are available', function () {
      beforeEach(function() {
        spyOn(controller, 'getRegionHeadings').and.returnValue(['us-west-1', 'us-east-2']);
      });

      it('has no regions selected should return all AZs', function () {
        let zones = ['us-west-1a', 'us-west-1c'];
        controller.availabilityZoneHeadings = zones;

        let results = controller.getAvailabilityZoneHeadings();
        expect(results).toEqual(zones);
      });

      it('should return us-west-1a zone when us-west-1 region is selected', function() {
        controller.availabilityZoneHeadings = ['us-west-1a', 'us-east-2c'];
        scope.sortFilter.region = { 'us-west-1': true };

        let results = controller.getAvailabilityZoneHeadings();
        expect(results).toEqual(['us-west-1a']);
      });

      it('should return empty list when region is selected that has no AZs', function() {
        controller.availabilityZoneHeadings = ['us-west-2a', 'us-east-2c'];
        scope.sortFilter.region = { 'us-west-1': true };

        let results = controller.getAvailabilityZoneHeadings();
        expect(results).toEqual([]);
      });

      it('should return all AZ when all regions are selected', function() {
        let zones = ['us-west-1a', 'us-east-2c'];
        controller.availabilityZoneHeadings = zones;
        scope.sortFilter.region = { 'us-west-1': true, 'us-east-2': true};

        let results = controller.getAvailabilityZoneHeadings();
        expect(results).toEqual(zones);
      });
    });

    describe('where regions have been filtered', function () {
      it('no regions available, no regions selected, should return no AZs', function () {
        spyOn(controller, 'getRegionHeadings').and.returnValue([]);
        let zones = ['us-west-1a', 'us-west-1c'];
        controller.availabilityZoneHeadings = zones;

        let results = controller.getAvailabilityZoneHeadings();
        expect(results).toEqual([]);
      });

      it('us-west-1 region available, no regions selected, should return us-west-1a AZ', function () {
        spyOn(controller, 'getRegionHeadings').and.returnValue(['us-west-1']);
        let zones = ['us-west-1a', 'us-east-2c'];
        controller.availabilityZoneHeadings = zones;

        let results = controller.getAvailabilityZoneHeadings();
        expect(results).toEqual(['us-west-1a']);
      });
    });
  });

  describe('getRegionHeadings', function () {
    beforeEach(function () {
      controller.regionsKeyedByAccount = {
        'my-aws-account': ['us-west-2', 'us-west-1', 'eu-east-2'],
        'my-google-account': ['us-central1', 'asia-east1', 'europe-west1']
      };
    });

    it('should return all regions if no accounts selected', function () {
      scope.sortFilter.account = {};
      let regions = ['us-west-2', 'us-west-1', 'eu-east-2', 'us-central1', 'asia-east1', 'europe-west1'];
      controller.regionHeadings = regions;

      let results = controller.getRegionHeadings();

      expect(results.length).toEqual(6);
    });

    it('should return regions for account if account is selected', function () {
      scope.sortFilter.account = { 'my-google-account' : true };
      let regions = ['us-west-2', 'us-west-1', 'eu-east-2', 'us-central1', 'asia-east1', 'europe-west1'];
      controller.regionHeadings = regions;

      let results = controller.getRegionHeadings();

      expect(results).toEqual(['us-central1', 'asia-east1', 'europe-west1']);
    });

    it('should return all regions if all accounts are selected', function () {
      scope.sortFilter.account = { 'my-google-account' : true, 'my-aws-account' : true };
      let regions = ['us-west-2', 'us-west-1', 'eu-east-2', 'us-central1', 'asia-east1', 'europe-west1'];
      controller.regionHeadings = regions;

      let results = controller.getRegionHeadings();

      expect(results.length).toEqual(6);
    });
  });
});
