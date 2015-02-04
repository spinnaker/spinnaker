'use strict';

angular
  .module('deckApp.loadBalancer', [
    'deckApp.loadBalancer.controller',
    'deckApp.loadBalancer.details.controller',
    'deckApp.loadBalancer.aws.create.controller',
    'deckApp.loadBalancer.gce.create.controller',
    'deckApp.loadBalancer.nav.controller'
  ]);
