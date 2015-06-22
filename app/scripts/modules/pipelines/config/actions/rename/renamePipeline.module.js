'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.rename', [
  require('../../../pipelines.module.js'),
  require('../../../../caches/deckCacheFactory.js'),
  require('../../../../utils/lodash.js'),
  require('../../services/dirtyPipelineTracker.service.js'),
]);
