'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.findImageFromTagsStage', [
  require('../../pipelineConfigProvider.js')
])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'findImageFromTags',
      label: 'Find Image from Tags',
      description: 'Finds an image to deploy from existing tags',
    });
  });
