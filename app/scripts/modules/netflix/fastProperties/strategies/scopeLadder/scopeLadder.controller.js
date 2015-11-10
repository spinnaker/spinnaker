'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.scopeLadder.strategy.controller', [
    require('../../../../core/utils/lodash'),
    require('../../fastProperty.write.service.js')
  ])
  .controller('ScopeLadderStrategyController', function(parentVM, fastPropertyWriter, modalInstance,  _) {
    let vm = parentVM;

    //vm.property = $scope.property;
    //vm.property.targetScope = $scope.selectedScope;
    //vm.property.impactCount = $scope.impactCount;
    //vm.selectedScope = $scope.selectedScope;
    //vm.clusters = $scope.clusters;
    //vm.isEditing = $scope.isEditing;
    //vm.appName = $scope.appName;
    vm.property.strategy.name = 'naive';

    vm.submit = () => {
      vm.property.startScope = vm.transformScope(vm.property.startScope);
      vm.property.targetScope = vm.transformScope(vm.property.targetScope);

      delete vm.property.env; //removing to help with downstream marshalling.
      delete vm.property.canary;

      console.info("SUBMITTING NAIVE: ", vm.property);

      fastPropertyWriter.upsertFastProperty(vm.property).then(
        function(result) {
          modalInstance.close(result);
        },
        function(error) {
          window.alert(JSON.stringify(error));
        });
    };

    vm.update = () => {
      var updatedParams = _(vm.property).omit(['ts', 'createdAsCanary']).value();
      vm.property.startScope = vm.transformScope(vm.property.startScope);
      vm.property.targetScope = vm.transformScope(vm.property.targetScope);

      delete vm.property.env; //removing to help with downstream marshalling.
      delete vm.property.canary;

      console.info("Updating NAIVE: ", vm.property);

      fastPropertyWriter.upsertFastProperty(updatedParams).then(
        function(result) {
          modalInstance.close(result);
        }
      );
    };

    return vm;
  })
 .name;
