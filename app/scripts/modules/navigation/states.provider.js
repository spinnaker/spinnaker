'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.states', [
  require('angular-ui-router'),
  require('./stateHelper.provider.js'),
  require('../delivery/states.js'),
  require('../core/cloudProvider/cloudProvider.registry.js'),
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

      // Handle legacy links to old security groups path
      $urlRouterProvider.when(
        '/applications/{application}/connections{path:.*}',
        '/applications/{application}/securityGroups'
      );

      var instanceDetails = {
        name: 'instanceDetails',
        url: '/instanceDetails/:provider/:instanceId',
        views: {
          'detail@home.applications.application.insight': {
            templateProvider: ['$templateCache', '$stateParams', 'cloudProviderRegistry', function($templateCache, $stateParams, cloudProviderRegistry) {
              return $templateCache.get(cloudProviderRegistry.getValue($stateParams.provider, 'instance.detailsTemplateUrl')); }],
            controllerProvider: ['$stateParams', 'cloudProviderRegistry', function($stateParams, cloudProviderRegistry) {
              return cloudProviderRegistry.getValue($stateParams.provider, 'instance.detailsController');
            }],
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
        url: '/serverGroupDetails/:provider/:accountId/:region/:serverGroup',
        views: {
          'detail@home.applications.application.insight': {
            templateProvider: ['$templateCache', '$stateParams', 'cloudProviderRegistry', function($templateCache, $stateParams, cloudProviderRegistry) {
              return $templateCache.get(cloudProviderRegistry.getValue($stateParams.provider, 'serverGroup.detailsTemplateUrl')); }],
            controllerProvider: ['$stateParams', 'cloudProviderRegistry', function($stateParams, cloudProviderRegistry) {
              return cloudProviderRegistry.getValue($stateParams.provider, 'serverGroup.detailsController');
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
        url: '/loadBalancerDetails/:provider/:accountId/:region/:vpcId/:name',
        params: {
          vpcId: {
            value: null,
            squash: true,
          },
        },
        views: {
          'detail@home.applications.application.insight': {
            templateProvider: ['$templateCache', '$stateParams', 'cloudProviderRegistry', function($templateCache, $stateParams, cloudProviderRegistry) {
              return $templateCache.get(cloudProviderRegistry.getValue($stateParams.provider, 'loadBalancer.detailsTemplateUrl')); }],
            controllerProvider: ['$stateParams', 'cloudProviderRegistry', function($stateParams, cloudProviderRegistry) {
              return cloudProviderRegistry.getValue($stateParams.provider, 'loadBalancer.detailsController');
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
        url: '/securityGroupDetails/:provider/:accountId/:region/:vpcId/:name',
        params: {
          vpcId: {
            value: null,
            squash: true,
          },
        },
        views: {
          'detail@home.applications.application.insight': {
            templateProvider: ['$templateCache', '$stateParams', 'cloudProviderRegistry', function($templateCache, $stateParams, cloudProviderRegistry) {
              return $templateCache.get(cloudProviderRegistry.getValue($stateParams.provider, 'securityGroup.detailsTemplateUrl')); }],
            controllerProvider: ['$stateParams', 'cloudProviderRegistry', function($stateParams, cloudProviderRegistry) {
              return cloudProviderRegistry.getValue($stateParams.provider, 'securityGroup.detailsController');
            }],
            controllerAs: 'ctrl'
          }
        },
        resolve: {
          resolvedSecurityGroup: ['$stateParams', function($stateParams) {
            return {
              name: $stateParams.name,
              accountId: $stateParams.accountId,
              provider: $stateParams.provider,
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

      var taskDetails = {
        name: 'taskDetails',
        url: '/:taskId',
        views: {},
        data: {
          pageTitleDetails: {
            title: 'Task Details',
            nameParam: 'taskId'
          }
        }
      };

      var insight = {
        name: 'insight',
        abstract: true,
        views: {
          'insight': {
            templateUrl: require('../insight/insight.html'),
            controller: 'InsightCtrl',
            controllerAs: 'insight'
          }
        },
        children: [
          {
          name: 'clusters',
          url: '/clusters',
          views: {
            'nav': {
              templateUrl: require('../clusterFilter/filterNav.html'),
              controller: 'ClusterFilterCtr',
              controllerAs: 'clustersFilters'
            },
            'master': {
              templateUrl: require('../cluster/all.html'),
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
              templateUrl: require('../loadBalancers/filter/filterNav.html'),
              controller: 'LoadBalancerFilterCtrl',
              controllerAs: 'loadBalancerFilters'
            },
            'master': {
              templateUrl: require('../loadBalancers/all.html'),
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
          ],
        },
        {
          url: '/securityGroups',
          name: 'securityGroups',
          views: {
            'nav': {
              templateUrl: require('../securityGroups/filter/filterNav.html'),
              controller: 'SecurityGroupFilterCtrl',
              controllerAs: 'securityGroupFilters'
            },
            'master': {
              templateUrl: require('../securityGroups/all.html'),
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
          ]
        }
        ]
      };

      var tasks = {
        name: 'tasks',
        url: '/tasks',
        views: {
          'insight': {
            templateUrl: require('../tasks/tasks.html'),
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
            templateUrl: require('../config/applicationConfig.view.html'),
            controller: 'ApplicationConfigController',
            controllerAs: 'config'
          },
        },
        data: {
          pageTitleSection: {
            title: 'Config'
          }
        }
      };

      var fastPropertyRollouts = {
        name: 'rollouts',
        url: '/rollouts',
        views: {
          'master': {
            templateUrl: require('../fastProperties/fastPropertyRollouts.html'),
            controller: 'FastPropertyRolloutController',
            controllerAs: 'rollout'
          }
        },
        data: {
          pageTitleSection: {
            title: 'Fast Property Rollout'
          }
        }
      };

      var appFastProperties = {
        name: 'properties',
        url: '/properties',
        views: {
          'insight': {
            templateUrl: require('../fastProperties/applicationProperties.html'),
            controller: 'ApplicationPropertiesController',
            controllerAs: 'fp'
          }
        },
        data: {
          pageTitleSection: {
            title: 'Fast Properties'
          }
        }
      };


      var application = {
        name: 'application',
        url: '/:application',
        views: {
          'main@': {
            templateUrl: require('../applications/application.html'),
            controller: 'ApplicationCtrl',
            controllerAs: 'ctrl'
          },
        },
        resolve: {
          app: ['$stateParams', 'applicationReader', function($stateParams, applicationReader) {
            return applicationReader.getApplication($stateParams.application, {tasks: true, executions: true})
              .then(
                function(app) {
                  return app;
                },
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
          config,
          appFastProperties,
        ],
      };

      var applications = {
        name: 'applications',
        url: '/applications',
        views: {
          'main@': {
            templateUrl: require('../applications/applications.html'),
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

      var fastProperties = {
        name: 'properties',
        url: '/properties',
        reloadOnSearch: false,
        views: {
          'master': {
            templateUrl: require('../fastProperties/properties.html'),
            controller: 'FastPropertiesController',
            controllerAs: 'fp'
          }
        }
      };

      var data = {
        name: 'data',
        url: '/data',
        reloadOnSearch: false,
        views: {
          'main@': {
            templateUrl: require('../fastProperties/main.html'),
            controller: 'FastPropertyDataController',
            controllerAs: 'data'
          }
        },
        data: {
          pageTitleMain: {
            label: 'Data'
          }
        },
        children: [
          fastProperties,
          fastPropertyRollouts,
        ]
      };


      var infrastructure = {
        name: 'infrastructure',
        url: '/infrastructure?q',
        reloadOnSearch: false,
        views: {
          'main@': {
            templateUrl: require('../search/infrastructure/infrastructure.html'),
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
        url: '/instance/:provider/:account/:region/:instanceId',
        views: {
          'main@': {
            templateUrl: require('../instance/standalone.html'),
            controllerProvider: ['$stateParams', 'cloudProviderRegistry', function($stateParams, cloudProviderRegistry) {
              return cloudProviderRegistry.getValue($stateParams.provider, 'instance.detailsController');
            }],
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
          app: function() {
            return {
              name: '(standalone instance)',
              registerAutoRefreshHandler: angular.noop,
              isStandalone: true,
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
          applications,
          infrastructure,
          data,
          standaloneInstance
        ],
      };

      stateHelperProvider.setNestedState(home);

    };

    this.$get = angular.noop;

  })
  .name;
