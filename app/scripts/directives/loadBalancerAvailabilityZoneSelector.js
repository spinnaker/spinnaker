'use strict';

angular.module('deckApp')
  .directive('loadBalancerAvailabilityZoneSelector', function() {
    return {
      restrict: 'E',
      scope: {
        availabilityZones: '=',
        region: '=',
        model: '=',
        usePreferredZones: '='
      },
      templateUrl: 'views/application/modal/serverGroup/aws/loadBalancerAvailabilityZoneDirective.html',
      controller: 'LoadBalnacerAvailabilityZoneSelectorCtrl as availabilityZoneCtrl',
    };
  })
  .controller('LoadBalnacerAvailabilityZoneSelectorCtrl', function($scope, $q, accountService) {
    $scope.model.usePreferredZones = angular.isDefined($scope.model.usePreferredZones) ? $scope.model.usePreferredZones : true;

    function setDefaultZones() {
      accountService.getPreferredZonesByAccount().then(
        function(preferredZonesLoader) {
          var defaultCredentials = $scope.model.credentials;
          var defaultRegion = $scope.region;

          var defaultZones = preferredZonesLoader[defaultCredentials];
          if (!defaultZones) {
            defaultZones = preferredZonesLoader['default'];
          }
          $scope.model.regionZones = angular.copy(defaultZones[defaultRegion]);
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
