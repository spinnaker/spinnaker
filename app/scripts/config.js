'use strict';

var angular = require('angular');

require('./services/states');

angular.module('deckApp')
  .config(function ($logProvider, statesProvider) {
    statesProvider.setStates();
    $logProvider.debugEnabled(true);
  });
