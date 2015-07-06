'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.config.actions.create', [
  require('./createPipelineButton.controller.js'),
  require('./createPipelineButton.directive.js'),
]).name;
