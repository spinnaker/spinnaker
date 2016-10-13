'use strict';

import {AUTHENTICATION} from 'core/authentication/authentication.module';

let angular = require('angular');

require('./pipelines.less');

module.exports = angular.module('spinnaker.core.pipeline', [
  require('exports?"ui.sortable"!angular-ui-sortable'),
  require('./config/pipelineConfig.module.js'),
  require('../cache/viewStateCache.js'),
  AUTHENTICATION,
  require('../notification/notifications.module.js'),
  require('../cache/deckCacheFactory.js'),
]);
