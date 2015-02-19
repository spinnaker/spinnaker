'use strict';

angular
  .module('deckApp.networking', [
    'deckApp.networking.controller',
    'deckApp.elasticIp.read.service',
    'deckApp.elasticIp.write.service'
  ]);
