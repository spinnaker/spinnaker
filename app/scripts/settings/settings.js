'use strict';

angular.module('deckApp')
  .constant('settings', {
    front50Url: 'http://front50-staging.prod.netflix.net',
    oortUrl: 'http://oort-staging.prod.netflix.net',
    mortUrl: 'http://mort-staging.prod.netflix.net',
    pondUrl: 'http://pond-staging.prod.netflix.net',
    awsMetadataUrl: 'http://spinnaker.test.netflix.net/aws',
    credentialsUrl: 'http://kato-staging.prod.netflix.net/',
    accounts: {
      prod: {
        challengeDestructiveActions: true
      }
    },
    pollSchedule: 30000
  });
