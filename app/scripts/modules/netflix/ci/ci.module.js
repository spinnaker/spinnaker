'use strict';

let angular = require('angular');

import {CI_STATES} from './ci.states';

require('./ci.less');

module.exports = angular
  .module('spinnaker.netflix.ci', [
    CI_STATES,
    require('./ci.dataSource'),
    require('./ci.controller'),
    require('./build.read.service'),
    require('./detail/detail.controller'),
    require('./detail/detailTab/detailTab.controller'),
  ]);
