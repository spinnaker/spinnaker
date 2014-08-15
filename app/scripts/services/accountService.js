'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('accountService', function(settings) {

    function challengeDestructiveActions(account) {
      return account && settings.accounts[account] && Boolean(settings.accounts[account].challengeDestructiveActions);
    }

    return {
      challengeDestructiveActions: challengeDestructiveActions
    };
  });
