'use strict';

let angular = require('angular');

require('./editPrecondition.html');

module.exports = angular
  .module('spinnaker.core.pipeline.config.preconditions.modal.controller', [
    require('../../../../utils/lodash.js'),
  ])
  .controller('EditPreconditionController', function ($scope, $modalInstance, precondition, _) {
    var vm = this;

    vm.precondition = angular.copy(precondition);
    vm.submit = function() {
      $modalInstance.close(vm.precondition);
    };

    return vm;
  }).name;
