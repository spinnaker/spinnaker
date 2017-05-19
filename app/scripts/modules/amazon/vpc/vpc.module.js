'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.vpc', [
    require('./vpcTag.directive')
  ]);
