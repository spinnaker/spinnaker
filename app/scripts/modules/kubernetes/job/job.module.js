'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.job.kubernetes', [
  require('./transformer.js'),
  require('./configure/CommandBuilder.js'),
  require('./configure/wizard/Clone.controller.js'),
  require('./configure/wizard/templateSelection.controller.js'),
  require('./details/details.controller.js'),
]);
