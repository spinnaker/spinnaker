'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.bake.modal.addExtendedAttribute', [])
  .controller('bakeStageAddExtendedAttributeController', function ($scope, $modalInstance, extendedAttribute) {

    var vm = this;
    $scope.extendedAttribute = angular.copy(extendedAttribute);

    vm.submit = function() {
      $modalInstance.close($scope.extendedAttribute);
    };

    return vm;
  });
