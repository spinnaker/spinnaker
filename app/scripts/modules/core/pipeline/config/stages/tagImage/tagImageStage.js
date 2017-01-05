'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.tagImageStage', [
  require('../../pipelineConfigProvider.js')
])
  .config(function (pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'upsertImageTags',
      label: 'Tag Image',
      description: 'Tags an image',
    });
  });
