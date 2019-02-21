'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.openstack.serverGroup.configure.wizard.advancedSettings', [
    require('@uirouter/angularjs').default,
    require('angular-ui-bootstrap'),
    require('../../../../common/cacheBackedMultiSelectField.directive').name,
  ])
  .controller('openstackServerGroupAdvancedSettingsCtrl', [
    '$scope',
    function($scope) {
      $scope.selectedAZs = $scope.command.zones
        ? $scope.command.zones.map(i => {
            return { id: i, name: i };
          })
        : [];

      $scope.updateAvailabilityZones = function() {
        $scope.allAvailabilityZones = getAvailabilityZones();
      };

      $scope.selectedAZsChanged = function() {
        $scope.command.zones = _.map($scope.selectedAZs, 'id');
      };

      $scope.$watch('selectedAZs', $scope.selectedAZsChanged);

      $scope.$watch(
        function() {
          return _.map(getAvailabilityZones(), 'id').join(',');
        },
        function() {
          $scope.selectedAZs = [];
          $scope.updateAvailabilityZones();
        },
      );

      $scope.$watch('command.credentials', $scope.updateAvailabilityZones);
      $scope.$watch('command.region', $scope.updateAvailabilityZones);

      function getAvailabilityZones() {
        var account = $scope.command.credentials;
        var region = $scope.command.region;
        if (!account || !region) {
          return [];
        } else {
          var ids = _.get(
            $scope.command,
            ['backingData', 'credentialsKeyedByAccount', account, 'regionToZones', region],
            [],
          );
          return ids.map(i => {
            return { id: i, name: i };
          });
        }
      }
    },
  ]);
