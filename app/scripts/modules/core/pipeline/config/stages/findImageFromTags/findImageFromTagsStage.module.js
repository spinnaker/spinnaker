'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.findImageFromTags', [
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('./findImageFromTagsStage.js'),
]);
