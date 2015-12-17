'use strict';
let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.feedback', [
    require('./feedback.modal.controller.js'),
    require('./feedback.directive.js')
  ]);
