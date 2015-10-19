'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.config', [])
  .config(function ($logProvider, statesProvider) {
    statesProvider.setStates();
    $logProvider.debugEnabled(true);
  })
  //.config(function ($compileProvider) {
  //  $compileProvider.debugInfoEnabled(false);
  //})
  .config(function(uiSelectConfig) {
    uiSelectConfig.theme = 'select2';
    uiSelectConfig.appendToBody = true;
  })
  .config(function($uibTooltipProvider) {
    $uibTooltipProvider.options({
      appendToBody: true
    });
    $uibTooltipProvider.setTriggers({
      'mouseenter focus': 'mouseleave blur'
    });
  })
  .config(function($modalProvider) {
    $modalProvider.options.backdrop = 'static';
    $modalProvider.options.keyboard = false;
  })
  .config(function(RestangularProvider, settings) {
    RestangularProvider.setBaseUrl(settings.gateUrl);
  })
  .config(function($httpProvider){
    $httpProvider.defaults.headers.patch = {
      'Content-Type': 'application/json;charset=utf-8'
    };
  })
  .config(function($compileProvider) {
    $compileProvider.aHrefSanitizationWhitelist(/^\s*(https?|mailto|hipchat):/);
  })
  .config(function($animateProvider) {
    $animateProvider.classNameFilter(/animated/);
  })
  .config(require('./modules/core/forms/uiSelect.decorator.js'))
  .name;
