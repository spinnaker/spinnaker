'use strict';

import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.findImageFromTagsStage', [
  PIPELINE_CONFIG_PROVIDER
])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'findImageFromTags',
      label: 'Find Image from Tags',
      description: 'Finds an image to deploy from existing tags',
    });
  });
