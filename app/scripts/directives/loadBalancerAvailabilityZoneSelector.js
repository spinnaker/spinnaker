'use strict';

angular.module('deckApp')
  .directive('loadBalancerAvailabilityZoneSelector', function() {
    return {
      restrict: 'E',
      scope: {
        availabilityZones: '=',
        region: '=',
        model: '=',
        usePreferredZones: '=',
        provider: '='
      },
      templateUrl: 'views/application/modal/serverGroup/aws/loadBalancerAvailabilityZoneDirective.html',
      controller: 'LoadBalancerAvailabilityZoneSelectorCtrl as availabilityZoneCtrl',
    };
  })
  .controller('LoadBalancerAvailabilityZoneSelectorCtrl', function($scope, $q, accountService) {
    $scope.model.usePreferredZones = angular.isDefined($scope.model.usePreferredZones) ? $scope.model.usePreferredZones : true;

    function setDefaultZones() {

      var defaultCredentials = $scope.model.credentials;
      var defaultRegion = $scope.region;

      accountService.getAvailabilityZonesForAccountAndRegion($scope.provider, defaultCredentials, defaultRegion).then(
        function(preferredZones) {
          $scope.model.regionZones = angular.copy(preferredZones);
        }
      );
    }

    setDefaultZones();

    $scope.$watch('region',  setDefaultZones);
    $scope.$watch('model.credentials', setDefaultZones);

    $scope.autoBalancingOptions = [
      { label: 'Enabled', value: true},
      { label: 'Manual', value: false}
    ];

    $scope.reset = function(preferred) {
      $scope.model.usePreferredZones = preferred;
      if(preferred) {
        setDefaultZones();
      }
    };

  });
