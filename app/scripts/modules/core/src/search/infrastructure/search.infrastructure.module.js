'use strict';

const angular = require('angular');

import {SEARCH_COMPONENT} from '../widgets/search.component';

import './infrastructure.less';

module.exports = angular.module('spinnaker.search.infrastructure', [
  require('./infrastructure.controller.js'),
  SEARCH_COMPONENT
]);
