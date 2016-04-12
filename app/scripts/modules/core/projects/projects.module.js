'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.projects', [
    require('./projects.controller.js'),
  ]);
