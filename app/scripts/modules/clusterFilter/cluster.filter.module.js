'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.cluster', [
    require('./instanceList.filter.js'),
    'cluster',
    'clusters.all',
    'cluster.filter.collapse',
    require('../utils/lodash.js'),
  ]);
