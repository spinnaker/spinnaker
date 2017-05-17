'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.controller', [
  require('angular-ui-router').default,
])
  .controller('PipelineConfigCtrl', function($scope, $stateParams) {

    let application = $scope.application;

    this.state = {
      pipelinesLoaded: false,
    };

    this.initialize = () => {
      this.pipelineConfig = _.find(application.pipelineConfigs.data, { id: $stateParams.pipelineId });
      if (!this.pipelineConfig) {
          this.pipelineConfig = _.find(application.strategyConfigs.data, { id: $stateParams.pipelineId });
          if(!this.pipelineConfig) {
            this.state.notFound = true;
          }
      }
      this.state.pipelinesLoaded = true;
    };

    if (!application.notFound) {
      application.pipelineConfigs.activate();
      application.pipelineConfigs.ready().then(this.initialize);
    }
  });
