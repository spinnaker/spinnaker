'use strict';

let angular = require('angular');

require('./navigation.less');

module.exports = angular.module('spinnaker.core.navigation.states.provider', [
  require('angular-ui-router'),
  require('./stateHelper.provider.js'),
  require('../delivery/states.js'),
  require('../cloudProvider/cloudProvider.registry.js'),
  require('../projects/project.controller.js'),
  require('../projects/dashboard/dashboard.controller.js'),
  require('../projects/service/project.read.service.js'),
  require('../overrideRegistry/override.registry.js'),
])
  .provider('states', function($stateProvider, $urlRouterProvider, stateHelperProvider, deliveryStates) {

    // Used to put additional states into the home and application views; can add to more states as needed
    let addedStates = {};
    this.addStateConfig = function(config) {
      if (!addedStates[config.parent]) {
        addedStates[config.parent] = [];
      }
      addedStates[config.parent].push(config.state);
    };

    function augmentChildren(state) {
      if (addedStates[state.name]) {
        state.children = (state.children || []).concat(addedStates[state.name]);
      }
    }

    this.setStates = function() {
      $urlRouterProvider.otherwise('/');
      // Don't crash on trailing slashes
      $urlRouterProvider.when('/{path:.*}/', ['$match', function($match) {
        return '/' + $match.path;
      }]);
      $urlRouterProvider.when('/applications/{application}', '/applications/{application}/clusters');
      $urlRouterProvider.when('/', '/infrastructure');
      $urlRouterProvider.when('/projects/{project}', '/projects/{project}/dashboard');
      $urlRouterProvider.when('/projects/{project}/applications/{application}', '/projects/{project}/applications/{application}/clusters');

      var instanceDetails = {
        name: 'instanceDetails',
        url: '/instanceDetails/:provider/:instanceId',
        views: {
          'detail@../insight': {
            templateProvider: ['$templateCache', '$stateParams', 'cloudProviderRegistry', function($templateCache, $stateParams, cloudProviderRegistry) {
              return $templateCache.get(cloudProviderRegistry.getValue($stateParams.provider, 'instance.detailsTemplateUrl')); }],
            controllerProvider: ['$stateParams', 'cloudProviderRegistry', function($stateParams, cloudProviderRegistry) {
              return cloudProviderRegistry.getValue($stateParams.provider, 'instance.detailsController');
            }],
            controllerAs: 'ctrl'
          }
        },
        resolve: {
          overrides: () => { return {}; },
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
          },
          history: {
            type: 'instances',
          },
        }
      };

      var serverGroupDetails = {
        name: 'serverGroup',
        url: '/serverGroupDetails/:provider/:accountId/:region/:serverGroup',
        views: {
          'detail@../insight': {
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
          },
          history: {
            type: 'serverGroups',
          },
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
          'detail@../insight': {
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
          },
          history: {
            type: 'loadBalancers',
          },
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
          'detail@../insight': {
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
          },
          history: {
            type: 'securityGroups',
          },
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
              templateUrl: require('../cluster/filter/filterNav.html'),
              controller: 'ClusterFilterCtrl',
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
              templateUrl: require('../loadBalancer/filter/filterNav.html'),
              controller: 'LoadBalancerFilterCtrl',
              controllerAs: 'loadBalancerFilters'
            },
            'master': {
              templateUrl: require('../loadBalancer/all.html'),
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
              templateUrl: require('../securityGroup/filter/filterNav.html'),
              controller: 'SecurityGroupFilterCtrl',
              controllerAs: 'securityGroupFilters'
            },
            'master': {
              templateUrl: require('../securityGroup/all.html'),
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
            templateUrl: require('../task/tasks.html'),
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
            templateProvider: ['$templateCache', 'overrideRegistry', function($templateCache, overrideRegistry) {
              let template = overrideRegistry.getTemplate('applicationConfigView', require('../application/config/applicationConfig.view.html'));
              return $templateCache.get(template);
            }],
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

      function application(mainView, relativeUrl='') {
        let applicationConfig = {
          name: 'application',
          url: `${relativeUrl}/:application`,
          resolve: {
            app: ['$stateParams', 'applicationReader', function($stateParams, applicationReader) {
              return applicationReader.getApplication($stateParams.application, {tasks: true, executions: true, pipelineConfigs: true})
                .then(
                function(app) {
                  return app || { notFound: true, name: $stateParams.application };
                },
                function() {
                  return { notFound: true, name: $stateParams.application };
                }
              );
            }]
          },
          data: {
            pageTitleMain: {
              field: 'application'
            },
            history: {
              type: 'applications',
              keyParams: ['application']
            },
          },
          children: [
            insight,
            tasks,
            deliveryStates.executions,
            deliveryStates.configure,
            config,
          ],
        };
        augmentChildren(applicationConfig);
        applicationConfig.views = {};
        applicationConfig.views[mainView] = {
            templateUrl: require('../application/application.html'),
            controller: 'ApplicationCtrl',
            controllerAs: 'ctrl'
          };
        return applicationConfig;
      }

      var applications = {
        name: 'applications',
        url: '/applications',
        views: {
          'main@': {
            templateUrl: require('../application/applications.html'),
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
          application('main@')
        ],
      };

      var dashboard = {
        name: 'dashboard',
        url: '/dashboard',
        views: {
          detail: {
            templateUrl: require('../projects/dashboard/dashboard.html'),
            controller: 'ProjectDashboardCtrl',
            controllerAs: 'dashboardCtrl',
          }
        },
        data: {
          pageTitleSection: {
            title: 'Dashboard'
          }
        },
      };

      var project = {
        name: 'project',
        url: '/projects/{project}',
        resolve: {
          projectConfiguration: ['$stateParams', 'projectReader', function($stateParams, projectReader) {
            return projectReader.getProjectConfig($stateParams.project).then(
              (projectConfig) => projectConfig,
              () => { return { notFound: true, name: $stateParams.project }; }
            );
          }]
        },
        views: {
          'main@': {
            templateUrl: require('../projects/project.html'),
            controller: 'ProjectCtrl',
            controllerAs: 'ctrl',
          },
        },
        data: {
          pageTitleMain: {
            field: 'project'
          },
          history: {
            type: 'projects'
          }
        },
        children: [
          dashboard,
          application('detail', '/applications'),
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
              isStandalone: true,
            };
          },
          overrides: () => { return {}; },
        },
        data: {
          pageTitleDetails: {
            title: 'Instance Details',
            nameParam: 'instanceId'
          },
          history: {
            type: 'instances',
          },
        }
      };

      var home = {
        name: 'home',
        abstract: true,
        children: [
          applications,
          infrastructure,
          project,
          standaloneInstance
        ],
      };

      augmentChildren(home);

      stateHelperProvider.setNestedState(home);

    };

    this.$get = angular.noop;

  });
