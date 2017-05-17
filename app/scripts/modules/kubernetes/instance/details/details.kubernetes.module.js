'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.instance.details.kubernetes', [
  require('core/account/account.module.js'),
  require('./details.controller.js'),
]);
