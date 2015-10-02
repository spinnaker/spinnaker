'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('exports?"ui.sortable"!angular-ui-sortable'),
  require('../utils/lodash.js'),
  require('./config/pipelineConfig.module.js'),
  require('../core/cache/viewStateCache.js'),
  require('../core/authentication/authentication.module.js'),
  require('../notifications/notifications.module.js'),
  require('../core/cache/deckCacheFactory.js'),
]).name;
