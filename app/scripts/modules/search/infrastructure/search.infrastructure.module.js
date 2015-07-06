'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.search.infrastructure', [
  require('./infrastructure.controller.js')
]).name;
