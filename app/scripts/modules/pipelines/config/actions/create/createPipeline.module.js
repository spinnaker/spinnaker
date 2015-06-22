'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.create', [
  require('../../../../utils/lodash.js'),
  require('../../../pipelines.module.js'),
  require('../../../../caches/deckCacheFactory.js'),
]);
