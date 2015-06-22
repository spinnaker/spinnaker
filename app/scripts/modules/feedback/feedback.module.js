'use strict';
let angular = require('angular');

module.exports = angular
  .module('spinnaker.feedback', [
    require('./feedback.modal.controller.js'),
    require('./feedback.directive.js')
  ]);
