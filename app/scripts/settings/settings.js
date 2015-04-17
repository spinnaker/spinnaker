'use strict';

angular.module('deckApp.settings', [])
  .constant('settings', {
    feedbackUrl: 'http://hootch.test.netflix.net/submit',
    gateUrl: 'https://spinnaker-api-prestaging.prod.netflix.net',
    pollSchedule: 30000,
    providers: {
      aws: {
        defaults: {
          account: 'test',
          region: 'us-east-1'
        },
        primaryAccounts: ['prod', 'test'],
        primaryRegions: ['eu-west-1','us-east-1','us-west-1','us-west-2'],
        challengeDestructiveActions: ['prod'],
        preferredZonesByAccount: {
          prod: {
            'us-east-1': ['us-east-1c', 'us-east-1d', 'us-east-1e'],
            'us-west-1': ['us-west-1a', 'us-west-1c'],
            'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
            'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
            'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
            'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
            'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
            'sa-east-1': ['sa-east-1a', 'sa-east-1b']
          },
          test: {
            'us-east-1': ['us-east-1c', 'us-east-1d', 'us-east-1e'],
            'us-west-1': ['us-west-1a', 'us-west-1c'],
            'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
            'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
            'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
            'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
            'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
            'sa-east-1': ['sa-east-1a', 'sa-east-1b']
          },
          mceprod: {
            'us-east-1': ['us-east-1a', 'us-east-1b', 'us-east-1c', 'us-east-1d', 'us-east-1e'],
            'us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c'],
            'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
            'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
            'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
            'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
            'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
            'sa-east-1': ['sa-east-1a', 'sa-east-1b']
          },
          mcetest: {
            'us-east-1': ['us-east-1a', 'us-east-1b', 'us-east-1c', 'us-east-1d'],
            'us-west-1': ['us-west-1b', 'us-west-1c'],
            'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
            'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
            'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
            'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
            'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
            'sa-east-1': ['sa-east-1a', 'sa-east-1b']
          },
          default: {
            'us-east-1': ['us-east-1a', 'us-east-1b', 'us-east-1c', 'us-east-1d', 'us-east-1e'],
            'us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c'],
            'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
            'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
            'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
            'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
            'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
            'sa-east-1': ['sa-east-1a', 'sa-east-1b']
          }
        }
      }
    },
    whatsNew: {
      gistId: '32526cd608db3d811b38',
      fileName: 'news.md',
      accessToken: 'd47428caab832c12c5ef489974d4fbba1d6eed96',
    },
    authEnabled: false,
    feature: {
      notifications: false,
      blesk: false,
    },


});

window.tracking = {
  enabled: false, // set to true to enable GA tracking
  key: 'key goes here',
};
