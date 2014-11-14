'use strict';

angular.module('deckApp.delivery')
  .factory('executionsFixture', function(momentService) {
    return [{
      'id': '3',
      'name': 'Deploy To Prod',
      'application': 'gate',
      'status': 'EXECUTING',
      'startTime': momentService().startOf('day').valueOf(),
      'trigger': {
        'type': 'jenkins',
        'buildInfo': {
          'lastBuildLabel': 3,
          'name': 'SPINNAKER-package-deck',
        },
      },
      'stages': [{
        'name': 'init',
        'status': 'COMPLETED',
        'startTime': momentService().startOf('day').valueOf(),
        'endTime': momentService().startOf('day').add(15, 'minutes').valueOf(),
      },
      {
        'name': 'bake',
        'status': 'COMPLETED',
        'startTime': momentService().startOf('day').add(15, 'minutes').valueOf(),
        'endTime': momentService().startOf('day').add(27, 'minutes').valueOf(),
      },
      {
        'name': 'canary',
        'status': 'EXECUTING',
        'startTime': momentService().startOf('day').add(27, 'minutes').valueOf(),
      },
      {
        'name': 'deploy',
        'status': 'NOT_STARTED',
      }],
    },
    {
      'id': '4',
      'name': 'Deploy To Staging',
      'application': 'gate',
      'status': 'COMPLETED',
      'startTime': momentService().startOf('day').add(3, 'hours').valueOf(),
      'endTime': momentService().startOf('day').add(4, 'hours').valueOf(),
      'trigger': {
        'type': 'jenkins',
        'buildInfo': {
          'lastBuildLabel': 4,
          'name': 'SPINNAKER-package-deck',
        },
      },
      'stages': [{
        'name': 'init',
        'status': 'COMPLETED',
        'startTime': momentService().startOf('day').add(3, 'hours').valueOf(),
        'endTime': momentService().startOf('day').add(3, 'hours').add(4, 'minutes').valueOf(),
      }, {
        'name': 'bake',
        'status': 'COMPLETED',
        'startTime': momentService().startOf('day').add(3, 'hours').add(4, 'minutes').valueOf(),
        'endTime': momentService().startOf('day').add(3, 'hours').add(42, 'minutes').valueOf(),
      }, {
        'name': 'deploy',
        'status': 'COMPLETED',
        'startTime': momentService().startOf('day').add(3, 'hours').add(42, 'minutes').valueOf(),
        'endTime': momentService().startOf('day').add(4, 'hours').valueOf(),
      }],
    },
    {
      'id': '1',
      'name': 'Deploy To Prod',
      'application': 'gate',
      'status': 'FAILED',
      'startTime': momentService().startOf('day').subtract(6, 'hours').valueOf(),
      'endTime': momentService().startOf('day').subtract(6, 'hours').add(7, 'minutes').valueOf(),
      'trigger': {
        'type': 'jenkins',
        'buildInfo': {
          'lastBuildLabel': 1,
          'name': 'SPINNAKER-package-deck',
        },
      },
      'stages': [{
          'name': 'init',
          'status': 'FAILED',
          'startTime': momentService().startOf('day').subtract(6, 'hours').valueOf(),
          'endTime': momentService().startOf('day').subtract(6, 'hours').add(7, 'minutes').valueOf(),
      }],
    },
    {
      'id': '2',
      'name': 'Deploy To Prod',
      'application': 'gate',
      'status': 'FAILED',
      'startTime': momentService().startOf('day').subtract(6, 'hours').add(32, 'minutes').valueOf(),
      'endTime': momentService().startOf('day').subtract(5, 'hours').add(7, 'minutes').valueOf(),
      'trigger': {
        'type': 'jenkins',
        'buildInfo': {
          'lastBuildLabel': 2,
          'name': 'SPINNAKER-package-deck',
        },
      },
      'stages': [{
        'name': 'init',
        'status': 'COMPLETED',
        'startTime': momentService().startOf('day').subtract(6, 'hours').add(32, 'minutes').valueOf(),
        'endTime': momentService().startOf('day').subtract(6, 'hours').add(41, 'minutes').valueOf(),
      },
      {
        'name': 'bake',
        'status': 'FAILED',
        'startTime': momentService().startOf('day').subtract(6, 'hours').add(41, 'minutes').valueOf(),
        'endTime': momentService().startOf('day').subtract(5, 'hours').add(7, 'minutes').valueOf(),
      }],
    }
  ];

});
