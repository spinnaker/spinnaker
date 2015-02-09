'use strict';

angular
  .module('deckApp.securityGroup', [
    'deckApp.securityGroup.all.controller',
    'deckApp.securityGroup.single.controller',
    'deckApp.securityGroup.rollup',
    'deckApp.securityGroup.read.service',
    'deckApp.securityGroup.write.service',
    'deckApp.securityGroup.counts',
    'deckApp.securityGroup.aws.details.controller',
    'deckApp.securityGroup.aws.edit.controller',
    'deckApp.securityGroup.aws.create.controller',
    'deckApp.securityGroup.navigation.controller'
  ]);
