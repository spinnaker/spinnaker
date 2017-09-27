'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.findImageFromTags', [
  require('../stage.module.js').name,
  require('../core/stage.core.module.js').name,
  require('./findImageFromTagsStage.js').name,
]);
