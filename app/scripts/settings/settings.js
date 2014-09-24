'use strict';

angular.module('deckApp')
  .constant('settings', {
    front50Url: 'http://front50.prod.netflix.net',
    oortUrl: 'http://oort.prod.netflix.net',
    mortUrl: 'http://mort.prod.netflix.net',
    pondUrl: 'http://pond.prod.netflix.net',
    awsMetadataUrl: 'http://spinnaker.test.netflix.net/aws',
    credentialsUrl: 'http://kato.prod.netflix.net/',
    accounts: {
      prod: {
        challengeDestructiveActions: true
      }
    },
    pollSchedule: 30000
  });
