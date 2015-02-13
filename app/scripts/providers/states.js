'use strict';

angular.module('deckApp.states', [
  'ui.router',
  'deckApp.stateHelper',
  'deckApp.delivery.states'
])
  .provider('states', function($stateProvider, $urlRouterProvider, stateHelperProvider, deliveryStates) {
    this.setStates = function() {
      $urlRouterProvider.otherwise('/');
      // Don't crash on trailing slashes
      $urlRouterProvider.when('/{path:.*}/', ['$match', function($match) {
        return '/' + $match.path;
      }]);
      $urlRouterProvider.when('/applications/{application}', '/applications/{application}/clusters');
      $urlRouterProvider.when('/', '/applications');
      $urlRouterProvider.when(
        '/applications/{application}/clusters/{acct}/{q}?reg',
        ['$match', function ($match) {
          return '/applications/' + $match.application + '/clusters?q=cluster:' + $match.q + '&acct=' + $match.acct + '&reg=' + $match.reg;
        }]
      );

      var instanceDetails = {
        name: 'instanceDetails',
        url: '/instanceDetails?instanceId',
        views: {
          'detail@home.applications.application.insight': {
            templateUrl: 'views/application/instanceDetails.html',
            controller: 'InstanceDetailsCtrl',
            controllerAs: 'ctrl'
          }
        },
        resolve: {
          instance: ['$stateParams', function($stateParams) {
            return {
              instanceId: $stateParams.instanceId
            };
          }]
        },
        data: {
          pageTitleDetails: {
            title: 'Instance Details',
            nameParam: 'instanceId'
          }
        }
      };

      var serverGroupDetails = {
        name: 'serverGroup',
        url: '/serverGroupDetails?serverGroup&accountId&region&provider',
        views: {
          'detail@home.applications.application.insight': {
            templateProvider: ['$templateCache', '$stateParams', function($templateCache, $stateParams) {
              var provider = $stateParams.provider || 'aws';
              return $templateCache.get('scripts/modules/serverGroups/details/' + provider + '/serverGroupDetails.html'); }],
            controllerProvider: ['$stateParams', function($stateParams) {
              var provider = $stateParams.provider || 'aws';
              return provider + 'ServerGroupDetailsCtrl';
            }],
            controllerAs: 'ctrl'
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
        },
        data: {
          pageTitleDetails: {
            title: 'Server Group Details',
            nameParam: 'serverGroup',
            accountParam: 'accountId',
            regionParam: 'region'
          }
        }
      };

      var loadBalancerDetails = {
        name: 'loadBalancerDetails',
        url: '/loadBalancerDetails?name&accountId&region&vpcId&provider',
        views: {
          'detail@home.applications.application.insight': {
            templateProvider: ['$templateCache', '$stateParams', function($templateCache, $stateParams) {
              var provider = $stateParams.provider || 'aws';
              return $templateCache.get('scripts/modules/loadBalancers/details/' + provider + '/loadBalancerDetails.html'); }],
            controllerProvider: ['$stateParams', function($stateParams) {
              var provider = $stateParams.provider || 'aws';
              return provider + 'LoadBalancerDetailsCtrl';
            }],
            controllerAs: 'ctrl'
          }
        },
        resolve: {
          loadBalancer: ['$stateParams', function($stateParams) {
            return {
              name: $stateParams.name,
              accountId: $stateParams.accountId,
              region: $stateParams.region,
              vpcId: $stateParams.vpcId
            };
          }]
        },
        data: {
          pageTitleDetails: {
            title: 'Load Balancer Details',
            nameParam: 'name',
            accountParam: 'accountId',
            regionParam: 'region'
          }
        }
      };

      var securityGroupDetails = {
        name: 'securityGroupDetails',
        url: '/securityGroupDetails?name&accountId&region&vpcId&provider',
        views: {
          'detail@home.applications.application.insight': {
            templateProvider: ['$templateCache', '$stateParams', function($templateCache, $stateParams) {
              var provider = $stateParams.provider || 'aws';
              return $templateCache.get('scripts/modules/securityGroups/details/' + provider + '/securityGroupDetails.html'); }],
            controllerProvider: ['$stateParams', function($stateParams) {
              var provider = $stateParams.provider || 'aws';
              return provider + 'SecurityGroupDetailsCtrl';
            }],
            controllerAs: 'ctrl'
          }
        },
        resolve: {
          securityGroup: ['$stateParams', function($stateParams) {
            return {
              name: $stateParams.name,
              accountId: $stateParams.accountId,
              region: $stateParams.region,
              vpcId: $stateParams.vpcId,
            };
          }]
        },
        data: {
          pageTitleDetails: {
            title: 'Security Group Details',
            nameParam: 'name',
            accountParam: 'accountId',
            regionParam: 'region'
          }
        }
      };

      var notFound = {
        name: '404',
        url: '/404',
        views: {
          'main@': {
            templateUrl: 'views/404.html',
            controller: angular.noop,
          }
        }
      };

      var taskDetails = {
        name: 'taskDetails',
        url: '/:taskId',
        views: {
          'task-details': {
            templateUrl: 'scripts/modules/tasks/taskdetails.html',
            controller: 'TaskDetailsCtrl',
            controllerAs: 'taskDetail'
          }
        },
        resolve: {
          taskId: ['$stateParams', function($stateParams) {
            return $stateParams.taskId;
          }]
        }
      };

      var insight = {
        name: 'insight',
        abstract: true,
        views: {
          'insight': {
            templateUrl: 'scripts/modules/insight/insight.html',
            controller: 'InsightCtrl',
            controllerAs: 'insight'
          }
        },
        children: [
          {
          name: 'clusters',
          reloadOnSearch: false,
          url: '/clusters?q&primary&secondary&hideInstances&hideHealthy&hideDisabled&acct&reg&status&providerType&instanceType',
          views: {
            'nav': {
              templateUrl: 'scripts/modules/clusterFilter/filterNav.html',
              controller: 'ClusterFilterCtr',
              controllerAs: 'clustersFilters'
            },
            'master': {
              templateUrl: 'scripts/modules/cluster/all.html',
              controller: 'AllClustersCtrl',
              controllerAs: 'allClusters'
            }
          },
          data: {
            pageTitleSection: {
              title: 'Clusters'
            }
          },
          children: [
            loadBalancerDetails,
            serverGroupDetails,
            instanceDetails,
            securityGroupDetails,
          ],
        },
        {
          url: '/loadBalancers',
          name: 'loadBalancers',
          views: {
            'nav': {
              templateUrl: 'scripts/modules/loadBalancers/navigation.html',
              controller: 'LoadBalancersNavCtrl',
              controllerAs: 'ctrl'
            },
            'master': {
              templateUrl: 'scripts/modules/loadBalancers/all.html',
              controller: 'AllLoadBalancersCtrl',
              controllerAs: 'ctrl'
            }
          },
          data: {
            pageTitleSection: {
              title: 'Load Balancers'
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
                  templateUrl: 'scripts/modules/loadBalancers/loadBalancer/single.html',
                  controller: 'LoadBalancerCtrl',
                  controllerAs: 'ctrl'
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
              data: {
                pageTitleMain: {
                  title: 'Load Balancer',
                  nameParam: 'loadBalancer',
                  accountParam: 'loadBalancerAccount',
                  regionParam: 'loadBalancerRegion'
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
              templateUrl: 'scripts/modules/securityGroups/navigation.html',
              controller: 'SecurityGroupsNavCtrl',
              controllerAs: 'ctrl'
            },
            'master': {
              templateUrl: 'scripts/modules/securityGroups/all.html',
              controller: 'AllSecurityGroupsCtrl',
              controllerAs: 'ctrl'
            }
          },
          data: {
            pageTitleSection: {
              title: 'Security Groups'
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
                  templateUrl: 'scripts/modules/securityGroups/single.html',
                  controller: 'SecurityGroupCtrl',
                  controllerAs: 'ctrl'
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
              data: {
                pageTitleSection: {
                  title: 'Security Group',
                  nameParam: 'securityGroup',
                  accountParam: 'securityGroupAccount',
                  regionParam: 'securityGroupRegion'
                }
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
            templateUrl: 'scripts/modules/tasks/tasks.html',
            controller: 'TasksCtrl',
            controllerAs: 'tasks'
          },
        },
        data: {
          pageTitleSection: {
            title: 'Tasks'
          }
        },
        children: [taskDetails],
      };

      var config = {
        name: 'config',
        url: '/config',
        views: {
          'insight': {
            templateUrl: 'scripts/modules/config/config.html',
            controller: 'ConfigController',
            controllerAs: 'config'
          },
        },
        data: {
          pageTitleSection: {
            title: 'Config'
          }
        }
      };

      var application = {
        name: 'application',
        url: '/:application',
        views: {
          'main@': {
            templateUrl: 'scripts/modules/applications/application.html',
            controller: 'ApplicationCtrl',
            controllerAs: 'ctrl'
          },
        },
        resolve: {
          application: ['$stateParams', 'applicationReader', function($stateParams, applicationReader) {
            return applicationReader.getApplication($stateParams.application, {tasks: true})
              .then(
              function(app) { return app; },
              function() { return {notFound: true, name: $stateParams.application}; }
            );
          }]
        },
        data: {
          pageTitleMain: {
            field: 'application'
          }
        },
        children: [
          insight,
          tasks,
          deliveryStates.executions,
          deliveryStates.configure,
          config
        ],
      };

      var applications = {
        name: 'applications',
        url: '/applications',
        views: {
          'main@': {
            templateUrl: 'scripts/modules/applications/applications.html',
            controller: 'ApplicationsCtrl',
            controllerAs: 'ctrl'
          }
        },
        data: {
          pageTitleMain: {
            label: 'Applications'
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
            controller: 'InfrastructureCtrl',
            controllerAs: 'ctrl'
          }
        },
        data: {
          pageTitleMain: {
            label: 'Infrastructure'
          }
        }
      };

      var standaloneInstance = {
        name: 'standaloneInstance',
        url: '/instance/:account/:region/:instanceId',
        views: {
          'main@': {
            templateUrl: 'scripts/modules/instance/standalone.html',
            controller: 'InstanceDetailsCtrl',
            controllerAs: 'ctrl'
          }
        },
        resolve: {
          instance: ['$stateParams', function($stateParams) {
            return {
              instanceId: $stateParams.instanceId,
              account: $stateParams.account,
              region: $stateParams.region,
              noApplication: true
            };
          }],
          application: function() {
            return {
              name: '(standalone instance)',
              registerAutoRefreshHandler: angular.noop
            };
          }
        },
        data: {
          pageTitleDetails: {
            title: 'Instance Details',
            nameParam: 'instanceId'
          }
        }
      };

      var home = {
        name: 'home',
        abstract: true,
        children: [
          notFound,
          applications,
          infrastructure,
          standaloneInstance
        ],
      };

      stateHelperProvider.setNestedState(home);

    };

    this.$get = angular.noop;

  });
