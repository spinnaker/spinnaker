'use strict';

angular.module('deckApp.settings', [])
  .constant('settings', {
    feedbackUrl: 'hootch',
    gateUrl: '/gate',
    accounts: {
      prod: {
        challengeDestructiveActions: true
      }
    },
    pollSchedule: 30000,
    providers: ['aws'],
    primaryAccounts: ['default'],
    primaryRegions: ['eu-west-1','us-east-1','us-west-1','us-west-2'],
    defaults: {
      account: 'default',
      region: 'us-east-1'
    }
  });
