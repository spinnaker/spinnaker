'use strict';

let angular = require('angular');
const $ = require('jquery');

module.exports = angular.module('spinnaker.utils.jQuery', [])
  .factory('$', function() {
    return $;
  })
  .name;
