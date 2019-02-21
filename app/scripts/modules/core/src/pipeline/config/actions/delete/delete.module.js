'use strict';

const angular = require('angular');
import { isEmpty } from 'lodash';

import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';

module.exports = angular
  .module('spinnaker.core.pipeline.config.actions.delete', [require('@uirouter/angularjs').default])
  .controller('DeletePipelineModalCtrl', ['$scope', '$uibModalInstance', '$log', 'application', 'pipeline', '$state', function($scope, $uibModalInstance, $log, application, pipeline, $state) {
    this.cancel = $uibModalInstance.dismiss;

    $scope.viewState = {};

    $scope.pipeline = pipeline;

    this.deletePipeline = () => {
      $scope.viewState.deleting = true;
      return PipelineConfigService.deletePipeline(application.name, pipeline, pipeline.name).then(
        () => {
          const idsToUpdatedIndices = {};
          const isPipelineStrategy = pipeline.strategy === true;
          const data = isPipelineStrategy ? application.strategyConfigs.data : application.pipelineConfigs.data;
          data.splice(data.findIndex(p => p.id === pipeline.id), 1);
          data.forEach(function(pipeline, index) {
            if (pipeline.index !== index) {
              pipeline.index = index;
              idsToUpdatedIndices[pipeline.id] = index;
            }
          });
          if (!isEmpty(idsToUpdatedIndices)) {
            PipelineConfigService.reorderPipelines(application.name, idsToUpdatedIndices, isPipelineStrategy);
          }
          $state.go('^.executions', null, { location: 'replace' });
        },
        response => {
          $log.warn(response);
          $scope.viewState.deleting = false;
          $scope.viewState.deleteError = true;
          $scope.viewState.errorMessage = response.message || 'No message provided';
        },
      );
    };
  }]);
