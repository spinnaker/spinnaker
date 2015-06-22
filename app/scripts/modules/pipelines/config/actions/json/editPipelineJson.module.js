'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.editJson', [
  require('../../../pipelines.module.js'),
  require('../../../../caches/deckCacheFactory.js'),
  require('../../../../utils/lodash.js')
]);
