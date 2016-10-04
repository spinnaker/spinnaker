'use strict';

let angular = require('angular');
require('./ci.less');

module.exports = angular
  .module('spinnaker.netflix.ci', [
    require('./states'),
    require('./ci.dataSource'),
    require('./ci.controller'),
    require('./build.read.service'),
    require('./detail/detail.controller'),
    require('./detail/detailTab/detailTab.controller'),
  ]);
