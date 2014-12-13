'use strict';

angular.module('deckApp')
  .provider('states', function($stateProvider, $urlRouterProvider, stateHelperProvider, deliveryStates) {
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
        url: '/serverGroupDetails?serverGroup&accountId&region',
        views: {
          'detail@home.applications.application.insight': {
            templateUrl: 'views/application/serverGroupDetails.html',
            controller: 'ServerGroupDetailsCtrl',
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
        url: '/loadBalancerDetails?name&accountId&region&vpcId',
        views: {
          'detail@home.applications.application.insight': {
            templateUrl: 'views/application/loadBalancer/loadBalancerDetails.html',
            controller: 'LoadBalancerDetailsCtrl',
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
        url: '/securityGroupDetails?name&accountId&region',
        views: {
          'detail@home.applications.application.insight': {
            templateUrl: 'views/application/connection/securityGroupDetails.html',
            controller: 'SecurityGroupDetailsCtrl',
            controllerAs: 'ctrl'
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
            templateUrl: 'views/taskdetails.html',
            controller: 'TaskDetailsCtrl',
            controllerAs: 'ctrl'
          }
        },
        resolve: {
          taskId: ['$stateParams', function($stateParams) {
            return parseInt($stateParams.taskId);
          }]
        }
      };

      var insight = {
        name: 'insight',
        abstract: true,
        views: {
          'insight': {
            templateUrl: 'views/insight.html',
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
              templateUrl: 'views/application/cluster/all.html',
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
            {
              name: 'cluster',
              url: '/:account/:cluster',
              views: {
                'master@home.applications.application.insight': {
                  templateUrl: 'views/application/cluster/single.html',
                  controller: 'ClusterCtrl',
                  controllerAs: 'ctrl'
                }
              },
              resolve: {
                cluster: ['$stateParams', function ($stateParams) {
                  return {account: $stateParams.account, clusterName: $stateParams.cluster};
                }]
              },
              data: {
                pageTitleSection: {
                  title: 'Cluster',
                  nameParam: 'cluster',
                  accountParam: 'account'
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
              controller: 'LoadBalancersNavCtrl',
              controllerAs: 'ctrl'
            },
            'master': {
              templateUrl: 'views/application/loadBalancer/all.html',
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
                  templateUrl: 'views/application/loadBalancer/single.html',
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
              templateUrl: 'views/application/connection/navigation.html',
              controller: 'SecurityGroupsNavCtrl',
              controllerAs: 'ctrl'
            },
            'master': {
              templateUrl: 'views/application/connection/all.html',
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
                  templateUrl: 'views/application/connection/single.html',
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
            templateUrl: 'views/tasks.html',
            controller: 'TasksCtrl',
          },
        },
        data: {
          pageTitleSection: {
            title: 'Tasks'
          }
        },
        children: [taskDetails],
      };

      var application = {
        name: 'application',
        url: '/:application',
        views: {
          'main@': {
            templateUrl: 'views/application.html',
            controller: 'ApplicationCtrl',
            controllerAs: 'ctrl'
          },
        },
        resolve: {
          application: ['$stateParams', 'oortService', function($stateParams, oortService) {
            return oortService.getApplication($stateParams.application);
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
        ],
      };

      var applications = {
        name: 'applications',
        url: '/applications',
        views: {
          'main@': {
            templateUrl: 'views/applications.html',
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

      var home = {
        name: 'home',
        abstract: true,
        views: {
          'main@': {
            templateUrl: 'views/main.html',
            controller: 'MainCtrl',
            controllerAs: 'ctrl'

          }
        },
        children: [
          notFound,
          applications,
          infrastructure
        ],
      };

      stateHelperProvider.setNestedState(home);

    };

    this.$get = angular.noop;

  });
