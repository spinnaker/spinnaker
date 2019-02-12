'use strict';

import _ from 'lodash';
import { hri as HumanReadableIds } from 'human-readable-ids';

import { PipelineTemplateReader } from './templates/PipelineTemplateReader';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.config.controller', [require('@uirouter/angularjs').default])
  .controller('PipelineConfigCtrl', function($scope, $stateParams, app) {
    this.application = app;
    this.state = {
      pipelinesLoaded: false,
    };

    this.containsJinja = source => source && (source.includes('{{') || source.includes('{%'));

    this.initialize = () => {
      this.pipelineConfig = _.find(app.pipelineConfigs.data, { id: $stateParams.pipelineId });

      if (this.pipelineConfig && this.pipelineConfig.expectedArtifacts) {
        for (const artifact of this.pipelineConfig.expectedArtifacts) {
          if (!artifact.displayName || artifact.displayName.length === 0) {
            // default display names are created for the sake of readability for pipelines that existed
            // before display names were introduced to expected artifacts
            const { kind, name } = artifact.matchArtifact;

            const dockerDisplayName = () => {
              const [, project = 'no-project', image = 'no-image'] = name.split('/');
              return `DOCKER-IMAGE-${project}/${image}`;
            };

            const bucketDisplayName = () => {
              const [, bucket = 'no-bucket', ...path] = name.split(/\/+/);
              return `${kind.toUpperCase()}-${bucket}/${path.join('/') || 'no-file'}`;
            };

            const httpDisplayName = () => {
              const [, host = 'no-host', ...file] = name.split(/\/+/);
              return `HTTP-${host}/${file.join('/') || 'no-file'}`;
            };

            switch (kind) {
              case 'docker':
                artifact.displayName = dockerDisplayName();
                break;
              case 'gcs':
              case 's3':
                artifact.displayName = bucketDisplayName();
                break;
              case 'http':
                artifact.displayName = httpDisplayName();
                break;
              case 'bitbucket':
              case 'github':
                artifact.displayName = `${kind.toUpperCase()}-${name}`;
                break;
              case 'helm':
                artifact.displayName = `HELM-${name}:${artifact.matchArtifact.version}`;
                break;
              case 'maven':
              case 'ivy':
                artifact.displayName = `${kind.toUpperCase()}-${name}`;
                break;
              default:
                artifact.displayName = HumanReadableIds.random();
            }
          }
        }
      }

      if (this.pipelineConfig && this.pipelineConfig.type === 'templatedPipeline') {
        this.isTemplatedPipeline = true;
        this.hasDynamicSource = this.containsJinja(this.pipelineConfig.config.pipeline.template.source);
        if (!this.pipelineConfig.isNew) {
          return PipelineTemplateReader.getPipelinePlan(this.pipelineConfig, $stateParams.executionId)
            .then(plan => (this.pipelinePlan = plan))
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
      if (this.pipelineConfig) {
        this.pipelineConfig = _.cloneDeep(this.pipelineConfig);
      }
    };

    if (!app.notFound) {
      app.pipelineConfigs.activate();
      app.pipelineConfigs
        .ready()
        .then(this.initialize)
        .then(() => (this.state.pipelinesLoaded = true));
    }
  });
