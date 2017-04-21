'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.actions.create.CreatePipelineButtonCtrl', [
])
  .controller('CreatePipelineButtonCtrl', function($scope, $state) {
    this.showCreatePipelineModal = false;

    this.showCallBack = (shouldShowModal) => {
      this.showCreatePipelineModal = shouldShowModal;
      $scope.$digest();
    };

    this.createPipeline = () => this.showCreatePipelineModal = true;

    this.goToPipelineConfig = (id) => {
      if (!$state.current.name.includes('.executions.execution')) {
        $state.go('^.pipelineConfig', { pipelineId: id });
      } else {
        $state.go('^.^.pipelineConfig', { pipelineId: id });
      }
    };
  });
