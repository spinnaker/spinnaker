'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.controller', [
  require('angular-ui-router'),
  require('./services/pipelineConfigService.js'),
  require('../../utils/lodash.js'),
  require('../../pageTitle/pageTitle.service.js'),
  require('./services/dirtyPipelineTracker.service.js'),
])
  .controller('PipelineConfigCtrl', function($scope, $rootScope, $timeout, $stateParams, _, $q, $window,
                                             pageTitleService, dirtyPipelineTracker) {

    let application = $scope.application;

    this.state = {
      pipelinesLoaded: false,
    };

    this.initialize = () => {
      this.pipelineConfig = _.find(application.pipelineConfigs, { id: $stateParams.pipelineId });
      if (!this.pipelineConfig) {
          this.pipelineConfig = _.find(application.strategyConfigs, { id: $stateParams.pipelineId });
          if(!this.pipelineConfig) {
            this.state.notFound = true;
          }
      }
      this.state.pipelinesLoaded = true;
    };

    if (!application.pipelineConfigs || !application.pipelineConfigs.length) {
      application.pipelineConfigRefreshStream.take(1).subscribe(this.initialize);
    } else {
      this.initialize();
    }

    function getWarningMessage() {
      return 'You have unsaved changes.\nAre you sure you want to navigate away from this page?';
    }

    var confirmPageLeave = $rootScope.$on('$stateChangeStart', function(event) {
      if (dirtyPipelineTracker.hasDirtyPipelines()) {
        if (!$window.confirm(getWarningMessage())) {
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
        return getWarningMessage();
      }
    };

    $scope.$on('$destroy', function() {
      confirmPageLeave();
      $window.onbeforeunload = undefined;
    });

  });
