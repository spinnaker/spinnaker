'use strict';

import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';
import { hri as HumanReadableIds } from 'human-readable-ids';
import _ from 'lodash';

import { PipelineTemplateReader } from './templates/PipelineTemplateReader';
import { PipelineTemplateV2Service } from './templates/v2/pipelineTemplateV2.service';

export const CORE_PIPELINE_CONFIG_PIPELINECONFIG_CONTROLLER = 'spinnaker.core.pipeline.config.controller';
export const name = CORE_PIPELINE_CONFIG_PIPELINECONFIG_CONTROLLER; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_PIPELINECONFIG_CONTROLLER, [UIROUTER_ANGULARJS]).controller('PipelineConfigCtrl', [
  '$scope',
  '$state',
  '$stateParams',
  'app',
  function ($scope, $state, $stateParams, app) {
    this.application = app;
    this.state = {
      pipelinesLoaded: false,
    };

    this.containsJinja = (source) => source && (source.includes('{{') || source.includes('{%'));

    this.initialize = () => {
      this.pipelineConfig = _.find(app.pipelineConfigs.data, { id: $stateParams.pipelineId });
      const isV2PipelineConfig =
        this.pipelineConfig && PipelineTemplateV2Service.isV2PipelineConfig(this.pipelineConfig);

      if (this.pipelineConfig && this.pipelineConfig.expectedArtifacts) {
        for (const artifact of this.pipelineConfig.expectedArtifacts) {
          if (!artifact.displayName || artifact.displayName.length === 0) {
            const { name } = artifact.matchArtifact;
            if (name) {
              artifact.displayName = name;
            } else {
              artifact.displayName = HumanReadableIds.random();
            }
          }
        }
      }

      if (this.pipelineConfig && this.pipelineConfig.type === 'templatedPipeline') {
        this.isTemplatedPipeline = true;
        this.isV2TemplatedPipeline = isV2PipelineConfig;
        this.hasDynamicSource =
          !isV2PipelineConfig && this.containsJinja(this.pipelineConfig.config.pipeline.template.source);

        if ($stateParams.new === '1') {
          this.pipelineConfig.isNew = true;
        }

        if (!this.pipelineConfig.isNew || isV2PipelineConfig) {
          return PipelineTemplateReader.getPipelinePlan(this.pipelineConfig, $stateParams.executionId)
            .then((plan) => (this.pipelinePlan = plan))
            .catch((error) => {
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
      if (this.pipelineConfig) {
        this.pipelineConfig = _.cloneDeep(this.pipelineConfig);
      }
    };

    if (!app.notFound && !app.hasError) {
      app.pipelineConfigs.activate();
      app.pipelineConfigs
        .refresh()
        .then(this.initialize)
        .then(() => (this.state.pipelinesLoaded = true));
    }
  },
]);
