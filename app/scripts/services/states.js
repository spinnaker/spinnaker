'use strict';

var angular = require('angular');

require('./statehelper');

angular.module('deckApp')
  .provider('states', function($stateProvider, $urlRouterProvider, stateHelperProvider) {
    this.setStates = function() {
      $urlRouterProvider.otherwise('/');
      $urlRouterProvider.when('/applications/{application}', '/applications/{application}/clusters');
      $urlRouterProvider.when('/', '/applications');

      var instanceDetails = {
        name: 'instanceDetails',
        url: '/instanceDetails?instanceId',
        views: {
          'detail@home.applications.application.insight': {
            templateUrl: 'views/application/instanceDetails.html',
            controller: 'InstanceDetailsCtrl as ctrl'
          }
        },
        resolve: {
          instance: ['$stateParams', function($stateParams) {
            return {
              instanceId: $stateParams.instanceId
            };
          }]
        }
      };

      var serverGroupDetails = {
        name: 'serverGroup',
        url: '/serverGroupDetails?serverGroup&accountId&region',
        views: {
          'detail@home.applications.application.insight': {
            templateUrl: 'views/application/serverGroupDetails.html',
            controller: 'ServerGroupDetailsCtrl as ctrl'
          }
        },
        resolve: {
          serverGroup: ['$stateParams', function($stateParams) {
            return {
              name: $stateParams.serverGroup,
              accountId: $stateParams.accountId,
              region: $stateParams.region
            };
          }]
        }
      };

      var loadBalancerDetails = {
        name: 'loadBalancerDetails',
        url: '/loadBalancerDetails?name&accountId&region',
        views: {
          'detail@home.applications.application.insight': {
            templateUrl: 'views/application/loadBalancer/loadBalancerDetails.html',
            controller: 'LoadBalancerDetailsCtrl as ctrl'
          }
        },
        resolve: {
          loadBalancer: ['$stateParams', function($stateParams) {
            return {
              name: $stateParams.name,
              accountId: $stateParams.accountId,
              region: $stateParams.region
            };
          }]
        }
      };

      var securityGroupDetails = {
        name: 'securityGroupDetails',
        url: '/securityGroupDetails?name&accountId&region',
        views: {
          'detail@home.applications.application.insight': {
            templateUrl: 'views/application/connection/securityGroupDetails.html',
            controller: 'SecurityGroupDetailsCtrl as ctrl'
          }
        },
        resolve: {
          securityGroup: ['$stateParams', function($stateParams) {
            return {
              name: $stateParams.name,
              accountId: $stateParams.accountId,
              region: $stateParams.region
            };
          }]
        }
      };

      var insight = {
        name: 'insight',
        abstract: true,
        views: {
          'insight': {
            templateUrl: 'views/insight.html',
          }
        },
        children: [
          {
          name: 'clusters',
          url: '/clusters?q',
          views: {
            'nav': {
              templateUrl: 'views/application/cluster/navigation.html',
              controller: 'ClustersNavCtrl as ctrl'
            },
            'master': {
              templateUrl: 'views/application/cluster/all.html',
              controller: 'AllClustersCtrl as ctrl'
            }
          },
          children: [
            loadBalancerDetails,
            serverGroupDetails,
            instanceDetails,
            securityGroupDetails,
            {
              name: 'cluster',
              url: '/:account/:cluster',
              views: {
                'master@home.applications.application.insight': {
                  templateUrl: 'views/application/cluster/single.html',
                  controller: 'ClusterCtrl as ctrl'
                }
              },
              resolve: {
                cluster: ['$stateParams', 'application', function ($stateParams, application) {
                  return application.getCluster($stateParams.account, $stateParams.cluster);
                }]
              },
              children: [loadBalancerDetails, serverGroupDetails, instanceDetails],
            }
          ],
        },
        {
          url: '/loadBalancers',
          name: 'loadBalancers',
          views: {
            'nav': {
              templateUrl: 'views/application/loadBalancer/navigation.html',
              controller: 'LoadBalancersNavCtrl as ctrl'
            },
            'master': {
              templateUrl: 'views/application/loadBalancer/all.html',
              controller: 'AllLoadBalancersCtrl as ctrl'
            }
          },
          children: [
            loadBalancerDetails,
            serverGroupDetails,
            instanceDetails,
            securityGroupDetails,
            {
              url: '/:loadBalancerAccount/:loadBalancerRegion/:loadBalancer',
              name: 'loadBalancer',
              views: {
                'master@home.applications.application.insight': {
                  templateUrl: 'views/application/loadBalancer/single.html',
                  controller: 'LoadBalancerCtrl as ctrl'
                }
              },
              resolve: {
                loadBalancer: ['$stateParams', function($stateParams) {
                  return {
                    name: $stateParams.loadBalancer,
                    region: $stateParams.loadBalancerRegion,
                    account: $stateParams.loadBalancerAccount
                  };
                }]
              },
              children: [loadBalancerDetails, serverGroupDetails, instanceDetails],
            }
          ],
        }, {
          url: '/connections',
          name: 'connections',
          views: {
            'nav': {
              templateUrl: 'views/application/connection/navigation.html',
              controller: 'SecurityGroupsNavCtrl as ctrl'
            },
            'master': {
              templateUrl: 'views/application/connection/all.html',
              controller: 'AllSecurityGroupsCtrl as ctrl'
            }
          },
          children: [
            loadBalancerDetails,
            serverGroupDetails,
            securityGroupDetails,
            {
              url: '/:securityGroupAccount/:securityGroupRegion/:securityGroup',
              name: 'connection',
              views: {
                'master@home.applications.application.insight': {
                  templateUrl: 'views/application/connection/single.html',
                  controller: 'SecurityGroupCtrl as ctrl'
                }
              },
              resolve: {
                securityGroup: ['$stateParams', function($stateParams) {
                  return {
                    account: $stateParams.securityGroupAccount,
                    name: $stateParams.securityGroup,
                    region: $stateParams.securityGroupRegion
                  };
                }]
              },
              children: [loadBalancerDetails, serverGroupDetails, securityGroupDetails]
            }
          ]
        }
        ]
      };

      var tasks = {
        name: 'tasks',
        url: '/tasks',
        views: {
          'insight': {
            templateUrl: 'views/tasks.html',
            controller: 'TasksCtrl',
          },
        },
        resolve: {
          tasks: ['$stateParams', 'pond', function($stateParams, pond) {
            return pond
              .one('applications', $stateParams.application)
              .all('tasks').getList();
          }],
        },
      };

      var application = {
        name: 'application',
        url: '/:application',
        views: {
          'main@': {
            templateUrl: 'views/application.html',
            controller: 'ApplicationCtrl as ctrl'
          },
        },
        resolve: {
          application: ['$stateParams', 'oortService', function($stateParams, oortService) {
            return oortService.getApplication($stateParams.application);
          }]
        },
        children: [
          insight,
          tasks,
        ],
      };

      var applications = {
        name: 'applications',
        url: '/applications',
        views: {
          'main@': {
            templateUrl: 'views/applications.html',
            controller: 'ApplicationsCtrl as ctrl'
          }
        },
        children: [
          application
        ],
      };

      var infrastructure = {
        name: 'infrastructure',
        url: '/infrastructure?q',
        reloadOnSearch: false,
        views: {
          'main@': {
            templateUrl: 'views/infrastructure.html',
            controller: 'InfrastructureCtrl as ctrl',
          }
        },
      };

      var home = {
        name: 'home',
        abstract: true,
        views: {
          'main@': {
            templateUrl: 'views/main.html',
            controller: 'MainCtrl as ctrl'
          }
        },
        children: [
          applications,
          infrastructure
        ],
      };

      stateHelperProvider.setNestedState(home);

    };

    this.$get = angular.noop;

  });
