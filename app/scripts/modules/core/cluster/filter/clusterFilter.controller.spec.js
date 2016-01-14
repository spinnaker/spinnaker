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
      scope = $rootScope.$new()
      controller = $controller('ClusterFilterCtrl', {
        $scope: scope,
        app: {
          registerAutoRefreshHandler: angular.noop
        }
      });
    })
  );

  describe('getAvailabilityZoneHeadings', function () {

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
      scope.sortFilter.region = { 'us-west-1': true, 'us-east-2c': true};

      let results = controller.getAvailabilityZoneHeadings();
      expect(results).toEqual(zones);
    });

  });
});
