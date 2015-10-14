'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinneker.fastProperty.aca.strategy.controller', [
    require('../../fastProperty.write.service.js')
  ])
  .controller('acaStrategyController', function($scope, fastPropertyWriter ) {
    var vm = this; //controllerAs: strategy
    vm.form = $scope.formScope.newFastPropertyForm;
    vm.property = $scope.property;
    vm.property.scope = $scope.selectedScope;
    vm.property.impactCount = $scope.impactCount;
    vm.selectedSope = $scope.selectedScope;
    vm.clusters = $scope.clusters;
    vm.isEditing = $scope.isEditing;
    vm.appName = $scope.appName;
    vm.property.strategyName = 'percentage';

    vm.aca = {
      name: `${$scope.appName}-${$scope.property.key}-${$scope.isEditing ? 'update' : 'create'}`
    };

    vm.clusterIsSet = () => {
      if(vm.property.scope && typeof vm.property.scope.cluster === 'undefined') {
        vm.form.$setValidity('noCluster', false);
        return false;
      }
      vm.form.$setValidity('noCluster', true);
      return true;
    };

    vm.submit = () => {
      vm.property.canary = Object.assign({}, vm.aca);
      delete vm.property.env; //removing to help with downstream marshalling.

      fastPropertyWriter.upsertFastProperty(vm.property).then(
        function(result) {
          $scope.$modalInstance.close(result);
        },
        function(error) {
          window.alert(JSON.stringify(error));
        });
    };
  })
  .name;
