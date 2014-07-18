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
    'ui.router'
  ])
  .run(function($state, $rootScope) {
    // This can go away when the next version of ui-router is available (0.2.11+)
    // for now, it's needed because ui-sref-active does not work on parent states
    // and we have to use ng-class. It's gross.
    $rootScope.$state = $state;
  })
  .config(function ($stateProvider, $urlRouterProvider, $logProvider) {
    $logProvider.debugEnabled(true);
    $urlRouterProvider.otherwise('/');

    $urlRouterProvider.when('/applications/{application}', '/applications/{application}/clusters');

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
          applications: function() {
            return [{
              'name' : 'CASS_GPSEMP_EUWEST1',
              'description' : 'Cassandra cluster for cass_gpsemp_euwest1',
              'email' : 'cde_admin@netflix.com',
              'owner' : 'Cloud Database Engineering',
              'type' : 'Standalone Application',
              'group' : null,
              'monitorBucketType' : 'application',
              'pdApiKey' : '626560c0b433012e3b1312313d009e57',
              'regions' : null,
              'tags' : null,
              'createTs' : '1367273703256',
              'updateTs' : '1367273703256'
            }, {
              'name' : 'ZOOKEEPERSERVER',
              'description' : 'Main ZooKeeper Server',
              'email' : 'dlplatformteam@netflix.com',
              'owner' : 'Platform Team',
              'type' : 'Standalone Application',
              'group' : '',
              'monitorBucketType' : 'application',
              'pdApiKey' : '9aab37d0a3d3012f277422000afc49b7',
              'regions' : null,
              'tags' : null,
              'createTs' : '1314146078914',
              'updateTs' : '1385575519526'
            }, {
              'name' : 'CASS_SEG_SKEEBALL',
              'description' : 'Cassandra cluster for cass_seg_skeeball',
              'email' : 'cde_admin@netflix.com',
              'owner' : 'CDE SEG',
              'type' : 'Standalone Application',
              'group' : '',
              'monitorBucketType' : 'application',
              'pdApiKey' : '626560c0b433012e3b1312313d009e57',
              'regions' : null,
              'tags' : 'cde,cassandra',
              'createTs' : '1399576071499',
              'updateTs' : '1399576071499'
            }];
          }
        }
      })

      .state('application', {
        abstract: true,
        url: '/:application',
        parent: 'applications',
        views: {
          'main@': {
            templateUrl: 'views/application.html',
            controller: 'ApplicationCtrl'
          }
        },
        resolve: {
          application: function($stateParams) {
            return {
              'name' : $stateParams.application,
              'description' : 'Cassandra cluster for cass_seg_skeeball',
              'email' : 'cde_admin@netflix.com',
              'owner' : 'CDE SEG',
              'type' : 'Standalone Application',
              'group' : '',
              'monitorBucketType' : 'application',
              'pdApiKey' : '626560c0b433012e3b1312313d009e57',
              'regions' : null,
              'tags' : 'cde,cassandra',
              'createTs' : '1399576071499',
              'updateTs' : '1399576071499'
            };
          }
        }
      })
      .state('clusters', {
        url: '/clusters',
        parent: 'application',
        views: {
          'nav': {
            templateUrl: 'views/application/cluster/navigation.html'
          },
          'master': {
            templateUrl: 'views/application/cluster/all.html'
          }
        }
      })
      .state('clusters.cluster', {
        url: '/:cluster',
        views: {
          'master@application': {
            templateUrl: 'views/application/cluster/single.html',
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
      .state('elbs', {
        url: '/elbs',
        parent: 'application',
        views: {
          'nav': {
            templateUrl: 'views/application/elb/navigation.html'
          },
          'master': {
            templateUrl: 'views/application/elb/all.html'
          }
        }
      })
      .state('elbs.elb', {
        url: '/:elb',
        views: {
          'master@application': {
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
        parent: 'application',
        views: {
          'nav': {
            templateUrl: 'views/application/connection/navigation.html'
          },
          'master': {
            templateUrl: 'views/application/connection/all.html'
          }
        }
      })
      .state('connections.connection', {
        url: '/:connection',
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
