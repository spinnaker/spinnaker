'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.controller', [
  require('@uirouter/angularjs').default,
])
  .controller('PipelineConfigCtrl', function($scope, $stateParams, app) {

    this.application = app;
    this.state = {
      pipelinesLoaded: false,
    };

    this.initialize = () => {
      this.pipelineConfig = _.find(app.pipelineConfigs.data, { id: $stateParams.pipelineId });
      if (!this.pipelineConfig) {
          this.pipelineConfig = _.find(app.strategyConfigs.data, { id: $stateParams.pipelineId });
          if(!this.pipelineConfig) {
            this.state.notFound = true;
          }
      }
      this.state.pipelinesLoaded = true;
    };

    if (!app.notFound) {
      app.pipelineConfigs.activate();
      app.pipelineConfigs.ready().then(this.initialize);
    }
  });
