'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.scopeLadder.strategy.controller', [
    require('../../../../core/utils/lodash'),
    require('../../fastProperty.write.service.js')
  ])
  .controller('ScopeLadderStrategyController', function(parentVM, fastPropertyWriter, modalInstance,  _) {
    let vm = parentVM;

    vm.property.strategy.name = 'naive';

    vm.submit = () => {
      vm.property.startScope = vm.transformScope(vm.property.startScope);
      vm.property.targetScope = vm.transformScope(vm.property.targetScope);

      delete vm.property.env; //removing to help with downstream marshalling.
      delete vm.property.canary;


      fastPropertyWriter.upsertFastProperty(vm.property).then(
        function(result) {
          modalInstance.close(result);
        },
        function(error) {
          vm.submititionError = `There was an issue submitting your Fast Property: ${error.message}`;
        });
    };

    vm.update = () => {
      var updatedParams = _(vm.property).omit(['ts', 'createdAsCanary']).value();
      vm.property.startScope = vm.transformScope(vm.property.startScope);
      vm.property.targetScope = vm.transformScope(vm.property.targetScope);

      delete vm.property.env; //removing to help with downstream marshalling.
      delete vm.property.canary;


      fastPropertyWriter.upsertFastProperty(updatedParams).then(
        function(result) {
          modalInstance.close(result);
        },
        function(error) {
          vm.submititionError = `There was an issue submitting your Fast Property: ${error.message}`;
        });
    };

    return vm;
  });
