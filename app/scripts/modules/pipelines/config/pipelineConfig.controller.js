'use strict';

angular.module('deckApp.pipelines.config.controller', [
  'ui.router',
  'deckApp.pipelines.config.service',
  'deckApp.utils.lodash',
])
  .controller('PipelineConfigCtrl', function($scope, $timeout, $stateParams, pipelineConfigService, _, $q) {

    $scope.state = {
      pipelinesLoaded: false
    };

    var ctrl = this;

    ctrl.updatePipelines = function(pipelines) {
      $q.all(pipelines.map(function(pipeline) {
        return pipelineConfigService.savePipeline(pipeline, true);
      }));
    };

    pipelineConfigService.getPipelinesForApplication($stateParams.application).then(function(pipelines) {
      // if there are pipelines without an index, fix that
      if (pipelines && pipelines.length && pipelines[0].index === undefined) {
        pipelines.forEach(function(pipeline, index) {
          pipeline.index = index;
        });
        ctrl.updatePipelines(pipelines);
      }
      $scope.application.pipelines = _.sortBy(pipelines, 'index');
      $scope.state.pipelinesLoaded = true;
    });

    $scope.pipelineSortOptions = {
      axis: 'y',
      delay: 150,
      placeholder: 'drop-placeholder',
      'ui-floating': false,
      start: function(e, ui) {
        ui.placeholder.width(ui.helper.width()).height(ui.helper.height());
      },
      stop: function() {
        var dirty = [];
        $scope.application.pipelines.forEach(function(pipeline, index) {
          if (pipeline.index !== index) {
            pipeline.index = index;
            dirty.push(pipeline);
          }
        });
        ctrl.updatePipelines(dirty);
      }
    };

  });
