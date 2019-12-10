'use strict';

const angular = require('angular');

export const CORE_PIPELINE_CONFIG_STAGES_BAKE_MODAL_ADDEXTENDEDATTRIBUTE_CONTROLLER_MODAL =
  'spinnaker.core.pipeline.stage.bake.modal.addExtendedAttribute';
export const name = CORE_PIPELINE_CONFIG_STAGES_BAKE_MODAL_ADDEXTENDEDATTRIBUTE_CONTROLLER_MODAL; // for backwards compatibility
angular
  .module(CORE_PIPELINE_CONFIG_STAGES_BAKE_MODAL_ADDEXTENDEDATTRIBUTE_CONTROLLER_MODAL, [])
  .controller('bakeStageAddExtendedAttributeController', [
    '$scope',
    '$uibModalInstance',
    'extendedAttribute',
    function($scope, $uibModalInstance, extendedAttribute) {
      var vm = this;
      $scope.extendedAttribute = angular.copy(extendedAttribute);

      vm.submit = function() {
        $uibModalInstance.close($scope.extendedAttribute);
      };

      return vm;
    },
  ]);
