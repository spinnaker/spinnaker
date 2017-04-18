'use strict';

import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.findAmiStage', [
  PIPELINE_CONFIG_PROVIDER
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'findImage',
      label: 'Find Image from Cluster',
      description: 'Finds an image to deploy from an existing cluster'
    });
  });

