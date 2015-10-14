'use strict';

let rx = require('rx');
let angular = require('angular');

module.exports = angular.module('spinnaker.core.utils.rx', [])
  .factory('RxService', function () {
    return rx;
  }
).name;
