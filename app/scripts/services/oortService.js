'use strict';


angular.module('deckApp')
  .factory('oortService', function (searchService, settings, $q, Restangular, _, $timeout,
                                    clusterService, loadBalancerService, pond, securityGroupService,
                                    scheduler, taskTracker, $exceptionHandler, scheduledCache) {

    var gateEndpoint = Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.gateUrl);

      RestangularConfigurer.addElementTransformer('applications', false, function(application) {

        function refreshApplication() {
          return getApplication(application.name).then(function (newApplication) {
            taskTracker.handleTaskUpdates(
              application.tasks,
              newApplication.tasks
            );

            deepCopyApplication(application, newApplication);
            application.autoRefreshHandlers.forEach(function(handler) {
              handler.call();
            });
            newApplication = null;
          });
        }

        application.autoRefreshHandlers = [];

        application.registerAutoRefreshHandler = function(method, scope) {
          application.autoRefreshHandlers.push(method);
          scope.$on('$destroy', function () {
            application.autoRefreshHandlers = application.autoRefreshHandlers.filter(function(handler) {
              return handler !== method;
            });
          });
        };

        function autoRefresh(scope) {
          if (application.autoRefreshEnabled) {
            var disposable = scheduler.subscribe(refreshApplication);
            scope.$on('$destroy', function () {
              application.disableAutoRefresh();
              disposable.dispose();
            });
          }
        }

        application.refreshImmediately = refreshApplication;

        application.disableAutoRefresh = function disableAutoRefresh() {
          application.autoRefreshEnabled = false;
        };

        application.enableAutoRefresh = function enableAutoRefresh(scope) {
          application.autoRefreshEnabled = true;
          autoRefresh(scope);
        };

        application.getCluster = function getCluster(accountName, clusterName) {
          var matches = application.clusters.filter(function (cluster) {
            return cluster.name === clusterName && cluster.account === accountName;
          });
          return matches.length ? matches[0] : null;
        };

        if (application.fromServer && application.clusters) {
          application.accounts = Object.keys(application.clusters);
        }
        return application;

      });
    });

    function listApplications() {
      return gateEndpoint
        .all('applications')
//        .withHttpConfig({cache: scheduledCache })
        .getList();
    }

    function getApplicationEndpoint(application) {
      return gateEndpoint.one('applications', application);
    }

    function deepCopyApplication(original, newApplication) {
      original.accounts = newApplication.accounts;
      original.clusters = newApplication.clusters;
      original.serverGroups = newApplication.serverGroups;
      original.loadBalancers = newApplication.loadBalancers;
      original.tasks = newApplication.tasks;
      original.securityGroups = newApplication.securityGroups;
      newApplication.accounts = null;
      newApplication.clusters = null;
      newApplication.loadBalancers = null;
      newApplication.tasks = null;
      newApplication.securityGroups = null;
    }

    function getApplication(applicationName) {
      var securityGroupsByApplicationNameLoader = securityGroupService.loadSecurityGroupsByApplicationName(applicationName),
          loadBalancersByApplicationNameLoader = loadBalancerService.loadLoadBalancersByApplicationName(applicationName),
          applicationLoader = getApplicationEndpoint(applicationName).get(),
          serverGroupLoader = clusterService.loadServerGroups(applicationName),
          taskLoader = pond.one('applications', applicationName).all('tasks').getList();

      var application, securityGroupAccounts, loadBalancerAccounts, serverGroups;

      var loadBalancerLoader, securityGroupLoader;

      return $q.all({
        securityGroups: securityGroupsByApplicationNameLoader,
        loadBalancersByApplicationName: loadBalancersByApplicationNameLoader,
        application: applicationLoader,
        tasks: taskLoader
      })
        .then(function(applicationLoader) {
          application = applicationLoader.application;
          securityGroupAccounts = _(applicationLoader.securityGroups).pluck('account').unique().value();
          loadBalancerAccounts = _(applicationLoader.loadBalancersByApplicationName).pluck('account').unique().value();
          application.accounts = _([applicationLoader.application.accounts, securityGroupAccounts, loadBalancerAccounts])
            .flatten()
            .compact()
            .unique()
            .value();
          application.tasks = angular.isArray(applicationLoader.tasks) ? applicationLoader.tasks : [];

          loadBalancerLoader = loadBalancerService.loadLoadBalancers(application, applicationLoader.loadBalancersByApplicationName);
          securityGroupLoader = securityGroupService.loadSecurityGroups(application);

          return $q.all({
            loadBalancers: loadBalancerLoader,
            securityGroups: securityGroupLoader,
            serverGroups: serverGroupLoader
          })
            .then(function(results) {
              serverGroups = results.serverGroups.plain();
              application.serverGroups = serverGroups;
              application.clusters = clusterService.createServerGroupClusters(serverGroups);

              application.loadBalancers = results.loadBalancers;
              loadBalancerService.normalizeLoadBalancersWithServerGroups(application);
              clusterService.normalizeServerGroupsWithLoadBalancers(application);
              securityGroupService.attachSecurityGroups(application, results.securityGroups, applicationLoader.securityGroups);

              return application;
            }, function(err) {
              $exceptionHandler(err, 'Failed to load application');
            });
        });
    }

    function listAWSLoadBalancers() {
      return gateEndpoint
        .all('loadBalancers')
        .withHttpConfig({cache: scheduledCache })
        .getList({provider: 'aws'});
    }

    function listGCELoadBalancers() {
      return gateEndpoint
        .all('loadBalancers')
        .getList({provider: 'gce'});
    }

    function getInstanceDetails(account, region, id) {
      return gateEndpoint.all('instances').one(account).one(region).one(id).get();
    }

    return {
      listApplications: listApplications,
      getApplication: getApplication,
      getInstanceDetails: getInstanceDetails,
      listAWSLoadBalancers: listAWSLoadBalancers,
      listGCELoadBalancers: listGCELoadBalancers,
      getApplicationWithoutAppendages: getApplicationEndpoint,
    };
  });
