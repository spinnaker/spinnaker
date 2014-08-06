'use strict';

/**
 * @ngdoc overview
 * @name deckApp
 * @description
 * # deckApp
 *
 * Main module of the application.
 */

var angular = require('angular');

require('angular-ui-router');
require('angular-bootstrap');
require('restangular');

angular.module('deckApp', [
    'ui.router',
    'ui.bootstrap',
    'restangular',
    'deckApp.templates'
  ])
  .run(function($state, $rootScope, $log, $exceptionHandler) {
    // This can go away when the next version of ui-router is available (0.2.11+)
    // for now, it's needed because ui-sref-active does not work on parent states
    // and we have to use ng-class. It's gross.
    //
    $rootScope.subscribeTo = function(observable) {
      this.subscribed = {
        data: undefined
      };

      observable.subscribe(function(data) {
        this.subscribed.data = data;
      }.bind(this), function(err) {
        $exceptionHandler(err, 'Failed to load data into the view.');
      }.bind(this));
    };



    $rootScope.$state = $state;
    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      $log.debug({
        event: event,
        toState: toState,
        toParams: toParams,
        fromState: fromState,
        fromParams: fromParams
      });
      $rootScope.routing = true;
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
      $rootScope.routing = false;
    });

    $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams, fromState, fromParams) {
      $log.debug({
        event: event,
        toState: toState,
        toParams: toParams,
        fromState: fromState,
        fromParams: fromParams
      });
      $rootScope.routing = false;
    });
  })
  .config(function(RestangularProvider) {
    RestangularProvider.addElementTransformer('applications', true, function(applications) {
      applications.forEach(function(application) {
        if (!application.attributes.createTs) {
          application.attributes.createTs = '0';
        }
        if (!application.attributes.updateTs) {
          application.attributes.updateTs = '0';
        }
      });
      return applications;
    });
  })
  .config(function ($stateProvider, $urlRouterProvider, $logProvider) {
    $logProvider.debugEnabled(true);
    $urlRouterProvider.otherwise('/');

    $urlRouterProvider.when('/applications/{application}', '/applications/{application}/clusters');
    $urlRouterProvider.when('/', '/applications');

    function addInstanceDetailsState(parent) {
      $stateProvider.state(parent + '.instanceDetails', {
        url: '/instanceDetails?instanceId',
        parent: parent,
        views: {
          'detail@insight': {
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
      });
    }

    function addServerGroupDetailsState(parent) {
      $stateProvider.state(parent + '.serverGroup', {
        url: '/serverGroupDetails?serverGroup&accountId&region',
        parent: parent,
        views: {
          'detail@insight': {
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
      });
    }

    function addLoadBalancerDetailsState(parent) {
      $stateProvider.state(parent + '.loadBalancerDetails', {
        url: '/loadBalancerDetails?name&accountId&region',
        parent: parent,
        views: {
          'detail@insight': {
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
      });
    }

    function addDetailsStates(parent) {
      addInstanceDetailsState(parent);
      addServerGroupDetailsState(parent);
      addLoadBalancerDetailsState(parent);
    }

    addDetailsStates('cluster');
    addDetailsStates('clusters');
    addDetailsStates('loadBalancer');
    addDetailsStates('loadBalancers');

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
            templateUrl: 'views/application/cluster/navigation.html',
            controller: 'ClustersNavCtrl'
          },
          'master': {
            templateUrl: 'views/application/cluster/all.html',
            controller: 'AllClustersCtrl'
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
          cluster: function($stateParams, application) {
            return application.getCluster($stateParams.account, $stateParams.cluster);
          }
        }
      })
      .state('loadBalancers', {
        url: '/loadBalancers',
        parent: 'insight',
        views: {
          'nav': {
            templateUrl: 'views/application/loadBalancer/navigation.html',
            controller: 'LoadBalancersNavCtrl'
          },
          'master': {
            templateUrl: 'views/application/loadBalancer/all.html',
            controller: 'AllLoadBalancersCtrl'
          },
          'filters@loadBalancers': {
            templateUrl: 'views/application/loadBalancer/filters.html'
          },
          'groupings@loadBalancers': {
            templateUrl: 'views/application/loadBalancer/groupings.html'
          }
        }
      })
      .state('loadBalancer', {
        url: '/:loadBalancerAccount/:loadBalancerRegion/:loadBalancer',
        parent: 'loadBalancers',
        views: {
          'master@insight': {
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
          'master@insight': {
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
      });

  })
  .controller('AllClustersCtrl', require('./controllers/AllClustersCtrl'))
  .controller('ApplicationCtrl', require('./controllers/ApplicationCtrl'))
  .controller('ClusterCtrl', require('./controllers/ClusterCtrl'))
  .controller('InstanceCtrl', require('./controllers/InstanceCtrl'))
  .controller('ServerGroupCtrl', require('./controllers/ServerGroupCtrl'))
  .controller('ApplicationsCtrl', require('./controllers/applications'))
  .controller('MainCtrl', require('./controllers/main'))
  .controller('TasksCtrl', require('./controllers/tasks'))
  .controller('ConfirmationModalCtrl', require('./controllers/ConfirmationModalCtrl'))
  .controller('ClustersNavCtrl', require('./controllers/ClustersNavCtrl'))
  .controller('AllLoadBalancersCtrl', require('./controllers/AllLoadBalancersCtrl'))
  .controller('LoadBalancersNavCtrl', require('./controllers/LoadBalancersNavCtrl'))
  .factory('confirmationModalService', require('./services/confirmationModal'))
  .factory('front50', require('./services/front50'))
  .factory('$', require('./services/jQuery'))
  .factory('_', require('./services/lodash'))
  .factory('momentService', require('./services/moment'))
  .factory('notifications', require('./services/notifications'))
  .factory('oortService', require('./services/oortService'))
  .factory('pond', require('./services/pond'))
  .factory('RxService', require('./services/rx'))
  .factory('isEmpty', require('./services/isEmpty'))
  .constant('settings', require('./services/settings'))
  .directive('arbitraryList', require('./directives/arbitraryList'))
  .directive('healthCounts', require('./directives/healthCounts'))
  .directive('insightMenu', require('./directives/insightmenu'))
  .directive('multiPageModal', require('./directives/multiPageModal'))
  .directive('modalPage', require('./directives/modalPage'))
  .directive('notifications', require('./directives/notifications'))
  .directive('serverGroup', require('./directives/serverGroup'))
  .directive('sortToggle', require('./directives/sorttoggle'))
  .directive('taskView', require('./directives/taskview'))
  .directive('underConstruction', require('./directives/underConstruction'))
  .directive('validateMin', require('./directives/validateMin'))
  .directive('validateMax', require('./directives/validateMax'))
  .directive('accountTag', require('./directives/accountTag'))
  .filter('dateFromTimestamp', require('./filters/datefromtimestamp'))
  .filter('relativeTime', require('./filters/relativeTime'))
  .filter('step', require('./filters/step'))
  .filter('stripCluster', require('./filters/stripCluster'))
  .filter('taskFilter', require('./filters/tasks'));
