'use strict';

const angular = require('angular');

require('./editPrecondition.html');

module.exports = angular
  .module('spinnaker.core.pipeline.config.preconditions.modal.controller', [])
  .controller('EditPreconditionController', [
    '$scope',
    '$uibModalInstance',
    'precondition',
    'strategy',
    'application',
    function($scope, $uibModalInstance, precondition, strategy, application) {
      var vm = this;

      vm.strategy = strategy;
      vm.application = application;
      vm.precondition = angular.copy(precondition);
      vm.submit = function() {
        $uibModalInstance.close(vm.precondition);
      };

      return vm;
    },
  ]);
