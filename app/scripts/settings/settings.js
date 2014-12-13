'use strict';

angular.module('deckApp.settings', [])
  .constant('settings', {
    front50Url: 'http://front50-prestaging.prod.netflix.net',
    oortUrl: 'http://oort.prod.netflix.net',
    mortUrl: 'http://mort-staging.prod.netflix.net',
    pondUrl: 'http://pond-prestaging.prod.netflix.net',
    katoUrl: 'http://kato-prestaging.prod.netflix.net',
    igorUrl: 'http://igor-prestaging.prod.netflix.net',
    mayoUrl: 'http://mayo-prestaging.prod.netflix.net',
    credentialsUrl: 'http://kato-prestaging.prod.netflix.net/',
    feedbackUrl: 'http://hootch.test.netflix.net/submit',
    gateUrl: 'http://spinnaker-api-prestaging.prod.netflix.net',
    accounts: {
      prod: {
        challengeDestructiveActions: true
      }
    },
    pollSchedule: 30000,
    providers: ['aws'],
    primaryAccounts: ['prod', 'test'],
    primaryRegions: ['eu-west-1','us-east-1','us-west-1','us-west-2'],
    defaults: {
      account: 'test',
      region: 'us-east-1'
    }
  });
