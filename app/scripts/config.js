'use strict';



angular.module('deckApp')
  .config(function ($logProvider, statesProvider) {
    statesProvider.setStates();
    $logProvider.debugEnabled(true);
  })
  .config(function(uiSelectConfig) {
    uiSelectConfig.theme = 'select2';
  })
  .config(function($tooltipProvider) {
    $tooltipProvider.options({
      appendToBody: true
    });
    $tooltipProvider.setTriggers({
      'mouseenter focus': 'mouseleave blur'
    });
  })
  .config(function($modalProvider) {
    $modalProvider.options.backdrop = 'static';
    $modalProvider.options.keyboard = false;
  })
;
