'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .constant('settings', {
    front50Url: 'http://front50.test.netflix.net',
    oortUrl: 'http://oort.prod.netflix.net',
    pondUrl: 'http://pond.test.netflix.net',
    accounts: {
      prod: {
        challengeDestructiveActions: true
      }
    }
  });
