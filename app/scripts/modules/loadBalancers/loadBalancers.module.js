'use strict';

angular
  .module('deckApp.loadBalancer', [
    'deckApp.loadBalancer.controller',
    'deckApp.loadBalancer.serverGroup',
    'deckApp.loadBalancer.tag',
    'deckApp.loadBalancer.aws.details.controller',
    'deckApp.loadBalancer.gce.details.controller',
    'deckApp.loadBalancer.aws.create.controller',
    'deckApp.loadBalancer.gce.create.controller',
    'deckApp.loadBalancer.nav.controller'
  ]);
