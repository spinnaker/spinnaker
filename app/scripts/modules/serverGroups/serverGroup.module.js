'use strict';

angular
  .module('deckApp.serverGroup', [
    'deckApp.serverGroup.write.service',
    'deckApp.serverGroup.transformer.service',
    'deckApp.serverGroup.configure.aws',
    'deckApp.serverGroup.configure.gce',
    'deckApp.serverGroup.configure.common',
  ]);
