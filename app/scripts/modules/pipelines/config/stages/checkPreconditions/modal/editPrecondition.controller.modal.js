'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.checkPreconditions.modal.editPrecondition', [])
  .controller('CheckPreconditionsEditPreconditionController', function ($scope, $modalInstance, precondition) {

    var vm = this;
    $scope.precondition = angular.copy(precondition);

    vm.submit = function() {
      $modalInstance.close($scope.precondition);
    };

    return vm;
  }).name;
