'use strict';

angular
  .module('deckApp.loadBalancer', [
    'deckApp.loadBalancer.write.service',
    'deckApp.loadBalancer.read.service'
  ]);