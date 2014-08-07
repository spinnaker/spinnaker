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
            templateUrl: 'views/application/instance.html',
            controller: 'InstanceCtrl'
          }
        },
        resolve: {
          instance: function($stateParams) {
            return {
              instanceId: $stateParams.instanceId
            };
          }
        }
      };

      var serverGroupDetails = {
        name: 'serverGroup',
        url: '/serverGroupDetails?serverGroup&accountId&region',
        views: {
          'detail@home.applications.application.insight': {
            templateUrl: 'views/application/serverGroup.html',
            controller: 'ServerGroupCtrl'
          }
        },
        resolve: {
          serverGroup: function($stateParams) {
            return {
              name: $stateParams.serverGroup,
              accountId: $stateParams.accountId,
              region: $stateParams.region
            };
          }
        }
      };

      var loadBalancerDetails = {
        name: 'loadBalancerDetails',
        url: '/loadBalancerDetails?name&accountId&region',
        views: {
          'detail@home.applications.application.insight': {
            templateUrl: 'views/application/loadBalancer/loadBalancerDetails.html',
            controller: 'LoadBalancerDetailsCtrl'
          }
        },
        resolve: {
          loadBalancer: function($stateParams) {
            return {
              name: $stateParams.name,
              accountId: $stateParams.accountId,
              region: $stateParams.region
            };
          }
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
          url: '/clusters',
          views: {
            'nav': {
              templateUrl: 'views/application/cluster/navigation.html',
              controller: 'ClustersNavCtrl'
            },
            'master': {
              templateUrl: 'views/application/cluster/all.html',
              controller: 'AllClustersCtrl'
            }
          },
          children: [
            loadBalancerDetails,
            serverGroupDetails,
            instanceDetails,
            {
              name: 'cluster',
              url: '/:account/:cluster',
              views: {
                'master@home.applications.application.insight': {
                  templateUrl: 'views/application/cluster/single.html',
                  controller: 'ClusterCtrl'
                }
              },
              resolve: {
                cluster: function ($stateParams, application) {
                  return application.getCluster($stateParams.account, $stateParams.cluster);
                }
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
              controller: 'LoadBalancersNavCtrl'
            },
            'master': {
              templateUrl: 'views/application/loadBalancer/all.html',
              controller: 'AllLoadBalancersCtrl'
            }
          },
          children: [
            loadBalancerDetails,
            serverGroupDetails,
            instanceDetails,
            {
              url: '/:loadBalancerAccount/:loadBalancerRegion/:loadBalancer',
              name: 'loadBalancer',
              views: {
                'master@home.applications.application.insight': {
                  templateUrl: 'views/application/loadBalancer/single.html',
                  controller: 'LoadBalancerCtrl'
                }
              },
              resolve: {
                loadBalancer: function($stateParams) {
                  return {
                    name: $stateParams.loadBalancer,
                    region: $stateParams.loadBalancerRegion,
                    account: $stateParams.loadBalancerAccount
                  };
                }
              },
              children: [loadBalancerDetails, serverGroupDetails, instanceDetails],
            }
          ],
        }, {
          url: '/connections',
          name: 'connections',
          views: {
            'nav': {
              templateUrl: 'views/application/connection/navigation.html'
            },
            'master': {
              templateUrl: 'views/application/connection/all.html'
            }
          },
          children: [
            {
            url: '/:connection',
            name: 'connection',
            views: {
              'master@home.applications.application.insight': {
                templateUrl: 'views/application/connection/single.html',
                controller: 'ClusterCtrl'
              }
            },
            resolve: {
              cluster: function($stateParams) {
                return {
                  name: $stateParams.cluster
                };
              }
            }
          },
          ],
        },
        ],
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
          tasks: function(pond) {
            // TODO: scope tasks to application
            return pond.all('task').getList();
          },
        },
      };

      var application = {
        name: 'application',
        url: '/:application',
        views: {
          'main@': {
            templateUrl: 'views/application.html',
            controller: 'ApplicationCtrl'
          },
        },
        resolve: {
          application: function($stateParams, oortService) {
            return oortService.getApplication($stateParams.application);
          }
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
            controller: 'ApplicationsCtrl'
          }
        },
        children: [
          application
        ],
      };

      var home = {
        name: 'home',
        abstract: true,
        views: {
          'main@': {
            templateUrl: 'views/main.html',
            controller: 'MainCtrl'
          }
        },
        children: [
          applications
        ],
      };

      stateHelperProvider.setNestedState(home);

    };

    this.$get = angular.noop;

  });
