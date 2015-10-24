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
        this.state.notFound = true;
      }
      this.state.pipelinesLoaded = true;
    };

    let configLoader = $q.when(null);
    if (!application.pipelineConfigs) {
      let deferred = $q.defer();
      configLoader = deferred.promise;
      if (!application.pipelineConfigsLoading) {
        application.reloadPipelineConfigs();
      }
      $scope.$on('pipelineConfigs-loaded', deferred.resolve);
    }

    configLoader.then(this.initialize);

    function constructBaseWarningMessage() {
      return 'You have unsaved changes.\nAre you sure you want to navigate away from this page?';
    }

    var confirmPageLeave = $rootScope.$on('$stateChangeStart', function(event) {
      if (dirtyPipelineTracker.hasDirtyPipelines()) {
        var message = constructBaseWarningMessage();
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

  }).name;
