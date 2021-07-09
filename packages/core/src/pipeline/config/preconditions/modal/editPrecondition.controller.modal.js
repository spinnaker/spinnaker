'use strict';

import * as angular from 'angular';

require('./editPrecondition.html');

export const CORE_PIPELINE_CONFIG_PRECONDITIONS_MODAL_EDITPRECONDITION_CONTROLLER_MODAL =
  'spinnaker.core.pipeline.config.preconditions.modal.controller';
export const name = CORE_PIPELINE_CONFIG_PRECONDITIONS_MODAL_EDITPRECONDITION_CONTROLLER_MODAL; // for backwards compatibility
angular
  .module(CORE_PIPELINE_CONFIG_PRECONDITIONS_MODAL_EDITPRECONDITION_CONTROLLER_MODAL, [])
  .controller('EditPreconditionController', [
    '$scope',
    '$uibModalInstance',
    'precondition',
    'strategy',
    'application',
    'upstreamStages',
    function ($scope, $uibModalInstance, precondition, strategy, application, upstreamStages) {
      const vm = this;

      vm.strategy = strategy;
      vm.application = application;
      vm.precondition = angular.copy(precondition);
      vm.upstreamStages = upstreamStages;
      vm.submit = function () {
        $uibModalInstance.close(vm.precondition);
      };

      return vm;
    },
  ]);
