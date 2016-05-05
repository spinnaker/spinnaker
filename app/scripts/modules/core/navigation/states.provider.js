'use strict';

let angular = require('angular');

require('./navigation.less');

module.exports = angular.module('spinnaker.core.navigation.states.provider', [
  require('angular-ui-router'),
  require('./stateHelper.provider.js'),
  require('../delivery/states.js'),
  require('../config/settings.js'),
  require('../cloudProvider/cloudProvider.registry.js'),
  require('../projects/project.controller.js'),
  require('../projects/dashboard/dashboard.controller.js'),
  require('../projects/service/project.read.service.js'),
  require('../overrideRegistry/override.registry.js'),
  require('../application/service/applications.read.service.js'),
])
  .provider('states', function($stateProvider, $urlRouterProvider, stateHelperProvider, deliveryStates, settings) {

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

      var multipleInstances = {
        name: 'multipleInstances',
        url: '/multipleInstances',
        views: {
          'detail@../insight': {
            templateUrl: require('../instance/details/multipleInstances.view.html'),
            controller: 'MultipleInstancesCtrl',
            controllerAs: 'vm'
          }
        },
        data: {
          pageTitleDetails: {
            title: 'Multiple Instances',
          },
        }
      };

      var multipleServerGroups = {
        name: 'multipleServerGroups',
        url: '/multipleServerGroups',
        views: {
          'detail@../insight': {
            templateUrl: require('../serverGroup/details/multipleServerGroups.view.html'),
            controller: 'MultipleServerGroupsCtrl',
            controllerAs: 'vm'
          }
        },
        data: {
          pageTitleDetails: {
            title: 'Multiple Server Groups',
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

      var jobDetails = {
        name: 'job',
        url: '/jobDetails/:provider/:accountId/:region/:job',
        views: {
          'detail@../insight': {
            templateProvider: ['$templateCache', '$stateParams', 'cloudProviderRegistry', function($templateCache, $stateParams, cloudProviderRegistry) {
              return $templateCache.get(cloudProviderRegistry.getValue($stateParams.provider, 'job.detailsTemplateUrl')); }],
            controllerProvider: ['$stateParams', 'cloudProviderRegistry', function($stateParams, cloudProviderRegistry) {
              return cloudProviderRegistry.getValue($stateParams.provider, 'job.detailsController');
            }],
            controllerAs: 'ctrl'
          }
        },
        resolve: {
          job: ['$stateParams', function($stateParams) {
            return {
              name: $stateParams.job,
              accountId: $stateParams.accountId,
              region: $stateParams.region
            };
          }]
        },
        data: {
          pageTitleDetails: {
            title: 'Job Details',
            nameParam: 'job',
            accountParam: 'accountId',
            regionParam: 'region'
          },
          history: {
            type: 'jobs',
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
            jobDetails,
            instanceDetails,
            securityGroupDetails,
            multipleInstances,
            multipleServerGroups,
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
            jobDetails,
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
            jobDetails,
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

      function application(mainView, relativeUrl = '') {
        let children = [insight, tasks, config];
        if (settings.feature && settings.feature.pipelines !== false) {
          children.push(deliveryStates.executions);
          children.push(deliveryStates.configure);
          children.push(deliveryStates.executionDetails);
        }

        let applicationConfig = {
          name: 'application',
          url: `${relativeUrl}/:application`,
          resolve: {
            app: ['$stateParams', 'applicationReader', function($stateParams, applicationReader) {
              return applicationReader.getApplication($stateParams.application)
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
          children: children,
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
            controllerAs: 'vm',
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
            controllerAs: 'vm',
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

      var projects = {
        name: 'projects',
        url: '/projects',
        views: {
          'main@': {
            templateUrl: require('../projects/projects.html'),
            controller: 'ProjectsCtrl',
            controllerAs: 'ctrl'
          }
        },
        data: {
          pageTitleMain: {
            label: 'Projects'
          }
        },
        children: [
        ],
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

      var standaloneSecurityGroup = {
        name: 'securityGroupDetails',
        url: '/securityGroupDetails/:provider/:accountId/:region/:vpcId/:name',
        params: {
          vpcId: {
            value: null,
            squash: true,
          },
        },
        views: {
          'main@': {
            templateUrl: require('../presentation/standalone.view.html'),
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
          }],
          app: ['$stateParams', 'securityGroupReader', function($stateParams, securityGroupReader) {
            // we need the application to have a security group index (so rules get attached and linked properly)
            // and its name should just be the name of the security group (so cloning works as expected)
            return securityGroupReader.loadSecurityGroups()
              .then((securityGroupsIndex) => {
                return {
                  name: $stateParams.name,
                  isStandalone: true,
                  securityGroupsIndex: securityGroupsIndex,
                };

              });
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

      var standaloneInstance = {
        name: 'instanceDetails',
        url: '/instance/:provider/:account/:region/:instanceId',
        views: {
          'main@': {
            templateUrl: require('../presentation/standalone.view.html'),
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
          projects,
          applications,
          infrastructure,
          project,
          standaloneInstance,
          standaloneSecurityGroup
        ],
      };

      augmentChildren(home);

      stateHelperProvider.setNestedState(home);

    };

    this.$get = angular.noop;

  });
