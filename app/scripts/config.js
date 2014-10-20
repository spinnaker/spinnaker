'use strict';



angular.module('deckApp')
  .config(function ($logProvider, statesProvider) {
    statesProvider.setStates();
    $logProvider.debugEnabled(true);
  })
  .config(function(uiSelectConfig) {
    uiSelectConfig.theme = 'select2';
  })
;
