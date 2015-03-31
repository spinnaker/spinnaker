'use strict';

angular
  .module('deckApp.fastproperties', [
    'deckApp.fastProperties.controller',
    'deckApp.applicationProperties.controller',
    'deckApp.fastPropertyScope.selection.directive',
    'deckApp.deleteFastProperty.controller',
    'deckApp.fastProperties.rollouts.controller',
    'deckApp.fastProperties.data.controller',
    'deckApp.fastProperty.progressBar.directive',
    'deckApp.fastProperty.constraints.directive',
  ]);
