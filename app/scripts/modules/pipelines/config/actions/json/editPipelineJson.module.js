'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.config.actions.editJson', [
  require('../../../pipelines.module.js'),
  require('../../../../caches/deckCacheFactory.js'),
  require('../../../../utils/lodash.js'),
  require('./editPipelineJsonModal.controller.js'),
]);

