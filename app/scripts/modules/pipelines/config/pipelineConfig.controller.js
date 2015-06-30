'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.config.controller', [
  require('angular-ui-router'),
  require('./services/pipelineConfigService.js'),
  require('utils/lodash.js'),
  require('../../pageTitle/pageTitleService.js'),
  require('./services/dirtyPipelineTracker.service.js'),
])
  .controller('PipelineConfigCtrl', function($scope, $rootScope, $timeout, $stateParams, _, $q, $window,
                                             pageTitleService, dirtyPipelineTracker, pipelineConfigService) {

    $scope.state = {
      pipelinesLoaded: false,
    };

    var ctrl = this;

    ctrl.updatePipelines = function(pipelines) {
      $q.all(pipelines.map(function(pipeline) {
        return pipelineConfigService.savePipeline(pipeline, true);
      }));
    };

    ctrl.initialize = function() {
      pipelineConfigService.getPipelinesForApplication($stateParams.application).then(function (pipelines) {
        $scope.application.pipelines = pipelines;
        $scope.state.pipelinesLoaded = true;
      });
    };

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

    function constructBaseWarningMessage() {
      var message = 'You have unsaved changes in the following pipelines:\n\n';
      dirtyPipelineTracker.list().forEach(function(pipeline) {
        message += '    * ' + pipeline + '\n';
      });
      return message;
    }

    var confirmPageLeave = $rootScope.$on('$stateChangeStart', function(event) {
      if (dirtyPipelineTracker.hasDirtyPipelines()) {
        var message = constructBaseWarningMessage();
        message += '\nAre you sure you want to navigate away from this page?';
        if (!$window.confirm(message)) {
          event.preventDefault();
          pageTitleService.handleRoutingSuccess({
            pageTitleMain: { label: $stateParams.application },
            pageTitleSection: { title: 'pipeline config' },
          });
          return false;
        }
      }
    });

    $window.onbeforeunload = function() {
      if (dirtyPipelineTracker.hasDirtyPipelines()) {
        return constructBaseWarningMessage();
      }
    };

    $scope.$on('$destroy', function() {
      confirmPageLeave();
      $window.onbeforeunload = undefined;
    });

    ctrl.initialize();

  });
