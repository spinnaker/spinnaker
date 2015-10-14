'use strict';

let angular = require('angular');
let $ = require('jquery');

module.exports = angular.module('spinnaker.core.utils.jQuery', [])
  .factory('$', function() {
    return $;
  }).name;
