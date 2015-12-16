/*global d3*/
'use strict';

let d3 = require('d3');
let angular = require('angular');

module.exports = angular.module('spinnaker.core.utils.d3', [])
  .factory('d3Service', function() {
    return d3;
  });
