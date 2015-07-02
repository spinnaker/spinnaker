'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../caches/viewStateCache.js'),
  require('./config/config.module.js'),
  require('../authentication/authentication.module.js'),
  require('utils/lodash.js'),
  require('../caches/deckCacheFactory.js'),
  require('exports?"ui.sortable"!angular-ui-sortable'),
]).name;
