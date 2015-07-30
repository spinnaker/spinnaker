'use strict';

let angular = require('angular');
let $ = require('jquery');

module.exports = angular.module('spinnaker.utils.jQuery', [])
  .factory('$', function() {
    return $;
  }).name;
