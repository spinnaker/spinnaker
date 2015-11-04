'use strict';

let angular = require('angular');

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular
  .module('spinnaker.netflix', [
    require('./whatsNew/whatsNew.directive.js'),
    require('./blesk/blesk.module.js'),
    require('./fastProperties/fastProperties.module.js'),
    require('./alert/alertHandler.js'),
    require('./feedback/feedback.module.js'),
    require('./pipeline/stage/canary/canaryStage.module.js'),
    require('../core/canary/index.js'), // TODO: move to Netflix module
  ]).name;
