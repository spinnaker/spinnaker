'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.scopeLadder.strategy.controller', [
    require('../../../../core/utils/lodash'),
    require('../../fastProperty.write.service.js')
  ])
  .controller('ScopeLadderStrategyController', function($scope, fastPropertyWriter, _) {
    let vm = this;

    vm.property = $scope.property;
    vm.property.scope = $scope.selectedScope;
    vm.property.impactCount = $scope.impactCount;
    vm.selectedScope = $scope.selectedScope;
    vm.clusters = $scope.clusters;
    vm.isEditing = $scope.isEditing;
    vm.appName = $scope.appName;
    vm.property.strategyName = 'naive';

    vm.submit = () => {
      fastPropertyWriter.upsertFastProperty(vm.property).then(
        function(result) {
          $scope.$modalInstance.close(result);
        },
        function(error) {
          window.alert(JSON.stringify(error));
        });
    };

    vm.update = () => {
      var updatedParams = _(vm.property).omit(['ts', 'createdAsCanary']).value();
      fastPropertyWriter.upsertFastProperty(updatedParams).then(
        function(result) {
          $scope.$modalInstance.close(result);
        }
      );
    };

  })
 .name;
