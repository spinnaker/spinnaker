'use strict';

const angular = require('angular');

export const CORE_PIPELINE_CONFIG_PIPELINECONFIGVIEW = 'spinnaker.core.pipeline.config.configView';
export const name = CORE_PIPELINE_CONFIG_PIPELINECONFIGVIEW; // for backwards compatibility
angular.module(CORE_PIPELINE_CONFIG_PIPELINECONFIGVIEW, []).directive('pipelineConfigView', function() {
  return {
    restrict: 'E',
    require: '^pipelineConfigurer',
    scope: {
      pipeline: '=',
      application: '=',
      viewState: '=',
      pipelineConfig: '=',
      stageFieldUpdated: '<',
      updatePipelineConfig: '<',
      isV2TemplatedPipeline: '<',
    },
    templateUrl: require('./pipelineConfigView.html'),
    link: function(scope, elem, attrs, pipelineConfigurerCtrl) {
      scope.pipelineConfigurerCtrl = pipelineConfigurerCtrl;
    },
  };
});
