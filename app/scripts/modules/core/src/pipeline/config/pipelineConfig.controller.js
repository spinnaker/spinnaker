'use strict';

import _ from 'lodash';

import {PIPELINE_TEMPLATE_SERVICE} from './templates/pipelineTemplate.service';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.controller', [
  require('@uirouter/angularjs').default,
  PIPELINE_TEMPLATE_SERVICE,
])
  .controller('PipelineConfigCtrl', function($scope, $stateParams, app, pipelineTemplateService) {

    this.application = app;
    this.state = {
      pipelinesLoaded: false,
    };

    this.containsJinja = source => source && (source.includes('{{') || source.includes('{%'));

    this.initialize = () => {
      this.pipelineConfig = _.find(app.pipelineConfigs.data, { id: $stateParams.pipelineId });
      if (this.pipelineConfig && this.pipelineConfig.type === 'templatedPipeline') {
        this.isTemplatedPipeline = true;
        this.hasDynamicSource = this.containsJinja(this.pipelineConfig.config.pipeline.template.source);
        if (!this.pipelineConfig.isNew) {
          return pipelineTemplateService.getPipelinePlan(this.pipelineConfig, $stateParams.executionId)
            .then(plan => this.pipelinePlan = plan)
            .catch(error => {
              this.templateError = error;
              this.pipelineConfig.isNew = true;
            });
        }
      } else if (!this.pipelineConfig) {
        this.pipelineConfig = _.find(app.strategyConfigs.data, { id: $stateParams.pipelineId });
        if (!this.pipelineConfig) {
          this.state.notFound = true;
        }
      }
    };

    if (!app.notFound) {
      app.pipelineConfigs.activate();
      app.pipelineConfigs.ready().then(this.initialize).then(() => this.state.pipelinesLoaded = true);
    }
  });
