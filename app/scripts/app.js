'use strict';

/**
 * @ngdoc overview
 * @name deckApp
 * @description
 * # deckApp
 *
 * Main module of the application.
 */
angular
  .module('deckApp', [
    'ui.router',
    'ui.bootstrap',
    'restangular',
  ])
  .run(function($state, $rootScope, $log) {
    // This can go away when the next version of ui-router is available (0.2.11+)
    // for now, it's needed because ui-sref-active does not work on parent states
    // and we have to use ng-class. It's gross.
    $rootScope.$state = $state;
    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      $log.debug({
        event: event,
        toState: toState,
        toParams: toParams,
        fromState: fromState,
        fromParams: fromParams
      });
    });

    $rootScope.$on('$stateChangeError', function(event, toState, toParams, fromState, fromParams, error) {
      $log.debug({
        event: event,
        toState: toState,
        toParams: toParams,
        fromState: fromState,
        fromParams: fromParams,
        error:error
      });
    });

    $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams, fromState, fromParams) {
      $log.debug({
        event: event,
        toState: toState,
        toParams: toParams,
        fromState: fromState,
        fromParams: fromParams
      });
    });
  })
  .config(function ($stateProvider, $urlRouterProvider, $logProvider) {
    $logProvider.debugEnabled(true);
    $urlRouterProvider.otherwise('/');

    $urlRouterProvider.when('/applications/{application}', '/applications/{application}/clusters');
    $urlRouterProvider.when('/', '/applications');

    $stateProvider
      .state('home', {
        url: '/',
        views: {
          'main': {
            templateUrl: 'views/main.html',
            controller: 'MainCtrl'
          }
        }
      })
      .state('applications', {
        url: '/applications',
        views: {
          'main': {
            templateUrl: 'views/applications.html',
            controller: 'ApplicationsCtrl'
          }
        },
        resolve: {
          applications: function(oortService) {
            return oortService.listApplications();
          }
        }
      })

      .state('application', {
        url: '/:application',
        parent: 'applications',
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
        }
      })

      .state('tasks', {
        url: '/tasks',
        parent: 'application',
        views: {
          'insight': 'views/tasks.html',
        },
      })

      .state('insight', {
        parent: 'application',
        abstract: true,
        views: {
          'insight': {
            templateUrl: 'views/insight.html',
          }
        }
      })
      .state('clusters', {
        url: '/clusters',
        parent: 'insight',
        views: {
          'nav': {
            templateUrl: 'views/application/cluster/navigation.html'
          },
          'master': {
            templateUrl: 'views/application/cluster/all.html'
          }
        }
      })
      .state('cluster', {
        url: '/:account/:cluster',
        parent: 'clusters',
        views: {
          'master@insight': {
            templateUrl: 'views/application/cluster/single.html',
            controller: 'ClusterCtrl'
          }
        },
        resolve: {
          cluster: function($stateParams) {
            return {
              name: $stateParams.cluster
            };
          },
          account: function($stateParams) {
            return {
              name: $stateParams.account
            };
          }
        }
      })
      .state('serverGroup', {
        url: '/serverGroup/:serverGroup',
        parent: 'cluster',
        views: {
          'details': {
            templateUrl: 'views/application/serverGroup.html',
            controller: 'ServerGroupCtrl'
          }
        },
        resolve: {
          serverGroup: function($stateParams, oortService) {
            return oortService.getServerGroup($stateParams.application, $stateParams.account, $stateParams.cluster, $stateParams.serverGroup);
          }
        }
      })
      .state('instance', {
        url: '/instance/:instance',
        parent: 'serverGroup',
        views: {
          'details@application': {
            templateUrl: 'views/application/instance.html',
            controller: 'InstanceCtrl'
          }
        },
        resolve: {
          instance: function($stateParams) {
            return {
              name: $stateParams.instance,
              application: $stateParams.application,
              account: $stateParams.account,
              cluster: $stateParams.cluster,
              serverGroup: $stateParams.serverGroup
            };
          }
        }
      })
      .state('elbs', {
        url: '/elbs',
        parent: 'insight',
        views: {
          'nav': {
            templateUrl: 'views/application/elb/navigation.html'
          },
          'master': {
            templateUrl: 'views/application/elb/all.html'
          }
        }
      })
      .state('elb', {
        url: '/:elb',
        parent: 'elbs',
        views: {
          'master': {
            templateUrl: 'views/application/elb/single.html',
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
      })
      .state('connections', {
        url: '/connections',
        parent: 'insight',
        views: {
          'nav': {
            templateUrl: 'views/application/connection/navigation.html'
          },
          'master': {
            templateUrl: 'views/application/connection/all.html'
          }
        }
      })
      .state('connection', {
        url: '/:connection',
        parent: 'connections',
        views: {
          'master@application': {
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
      })
    ;
  });
