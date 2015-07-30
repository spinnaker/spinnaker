'use strict';
let angular = require('angular');

module.exports = angular.module('spinnaker.help', [
  require('./helpField.directive.js'),
]).name;
