'use strict';

describe('Controller: ScalingActivitiesCtrl', function () {

  beforeEach(
    window.module(
      require('./scalingActivities.controller.js')
    )
  );

  beforeEach(function () {
    window.inject(function ($controller, $rootScope, serverGroupReader, $q) {
      var spec = this;

      this.$scope = $rootScope.$new();
      this.$q = $q;
      this.serverGroupReader = serverGroupReader;
      this.activities = [];

      spyOn(this.serverGroupReader, 'getScalingActivities').and.callFake(function () {
        return $q.when(spec.activities);
      });

      this.ctrl = $controller('ScalingActivitiesCtrl', {
        $scope: this.$scope,
        serverGroupReader: serverGroupReader,
        applicationName: 'app',
        account: 'test',
        clusterName: 'cluster',
        serverGroup: {
          name: 'asg-v001',
          region: 'us-east-1'
        },
        $modalInstance: jasmine.createSpyObj('$modalInstance', ['dismiss'])
      });
    });
  });

  describe('Activity grouping', function () {

    it('groups activities by cause, parsing availability zone from details, sorted by start date, newest first', function () {
      var spec = this;
      var activities = [
        {
          description: "Launching a new EC2 instance: i-05c487e8",
          details: '{"Availability Zone":"us-east-1d"}'
        },
        {
          description: "Launching a new EC2 instance: i-abcdefgh",
          details: '{"Availability Zone":"us-east-1e"}'
        }
      ];

      activities.forEach(function(activity) {
        spec.activities.push({ description: activity.description, cause: 'common cause', details: activity.details, startTime: 3});
      });

      spec.activities.push({ description: 'some other thing', cause: 'some other cause', startTime: 2});

      this.$scope.$digest();

      var result = this.$scope.activities;
      expect(result.length).toEqual(2);
      expect(result[0].cause).toBe('common cause');
      expect(result[1].cause).toBe('some other cause');
      expect(result[0].events[0].description).toEqual(activities[0].description);
      expect(result[0].events[1].description).toEqual(activities[1].description);
      expect(result[0].events[0].availabilityZone).toBe('us-east-1d');
      expect(result[0].events[1].availabilityZone).toBe('us-east-1e');
    });

    it('returns "unknown" for availability zone if details field not present, cannot be parsed, or does not contain key "Availability Zone"', function() {
      this.activities.push({ description: 'a', cause: 'some cause', details: 'not JSON so cannot be parsed'});
      this.activities.push({ description: 'b', cause: 'some cause'});
      this.activities.push({ description: 'c', cause: 'some cause', details: '{"Not Availability Zone":"us-east-1c"}'});
      this.activities.push({ description: 'd', cause: 'some cause', details: '{"Availability Zone":"us-east-1c"}'});

      this.$scope.$digest();

      var result = this.$scope.activities;
      expect(result.length).toEqual(1);
      expect(result[0].cause).toBe('some cause');
      expect(result[0].events[0].availabilityZone).toBe('unknown');
      expect(result[0].events[1].availabilityZone).toBe('unknown');
      expect(result[0].events[2].availabilityZone).toBe('unknown');
      expect(result[0].events[3].availabilityZone).toBe('us-east-1c');
    });

  });
});
