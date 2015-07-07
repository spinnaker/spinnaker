'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.cluster', [
    require('./instanceList.filter.js'),
    require('./ClusterFilterCtrl.js'),
    require('../cluster/allClusters.controller.js'),
    require('./collapsibleFilterSection.directive.js'),
    require('utils/lodash.js'),
  ]).name;
