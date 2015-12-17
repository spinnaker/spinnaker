'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.actions.create.CreatePipelineButtonCtrl', [
])
  .controller('CreatePipelineButtonCtrl', function($scope, $uibModal, $state) {
    this.createPipeline = function() {
      $uibModal.open({
        templateUrl: require('./createPipelineModal.html'),
        controller: 'CreatePipelineModalCtrl',
        controllerAs: 'createPipelineModalCtrl',
        resolve: {
          application: () => this.application,
        }
      }).result.then((id) => {
          if ($state.current.name.indexOf('.executions.execution') === -1) {
            $state.go('^.pipelineConfig', { pipelineId: id });
          } else {
            $state.go('^.^.pipelineConfig', { pipelineId: id });
          }
      });
    };
  });
