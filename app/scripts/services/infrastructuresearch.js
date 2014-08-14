'use strict';

var angular = require('angular');
angular.module('deckApp')
  .factory('infrastructureSearch', function() {
    return function(input) {
      // TODO: search cloud infrastructure
      console.log(input);
      return [
        {
          category: 'instance',
          displayName: 'Instances',
          results: [
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
          ],
        },
        {
          category: 'loadBalancer',
          displayName: 'Load Balancers',
          results: [
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
          ],
        },
        {
          category: 'cluster',
          displayName: 'Clusters',
          results: [
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
          ],
        },
        {
          category: 'serverGroup',
          displayName: 'ASGs',
          results: [
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
          ],
        },
        {
          category: 'securityGroup',
          displayName: 'Security Groups',
          results: [
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
            {
              displayName: 'i-4ec82a60',
              href: '/',
            },
          ],
        },
      ];
    };

  });
