'use strict';

angular
  .module('deckApp.applications.read.service', ['restangular'])
  .factory('applicationReader', function ($q, $exceptionHandler, Restangular, _, clusterService, taskTracker, tasksReader,
                                          loadBalancerReader, loadBalancerService, securityGroupService, scheduler) {

    function listApplications() {
      return Restangular
        .all('applications')
        .getList();
    }



    var gateEndpoint = Restangular.withConfig(function(RestangularConfigurer) {

      RestangularConfigurer.addElementTransformer('applications', false, function(application) {

        function refreshApplication() {
          application.reloadTasks();
          return getApplication(application.name).then(function (newApplication) {
            deepCopyApplication(application, newApplication);
            application.autoRefreshHandlers.forEach(function(handler) {
              handler.call();
            });
            newApplication = null;
          });
        }


        function registerAutoRefreshHandler(method, scope) {
          application.autoRefreshHandlers.push(method);
          scope.$on('$destroy', function () {
            application.autoRefreshHandlers = application.autoRefreshHandlers.filter(function (handler) {
              return handler !== method;
            });
          });
        }

        function autoRefresh(scope) {
          if (application.autoRefreshEnabled) {
            var disposable = scheduler.subscribe(refreshApplication);
            scope.$on('$destroy', function () {
              application.disableAutoRefresh();
              disposable.dispose();
            });
          }
        }

        function disableAutoRefresh () {
          application.autoRefreshEnabled = false;
        }

        function enableAutoRefresh (scope) {
          application.autoRefreshEnabled = true;
          autoRefresh(scope);
        }

        function getCluster (accountName, clusterName) {
          var matches = application.clusters.filter(function (cluster) {
            return cluster.name === clusterName && cluster.account === accountName;
          });
          return matches.length ? matches[0] : null;
        }

        function reloadTasks() {
          tasksReader.listAllTasksForApplication(application.name).then(function(tasks) {
            taskTracker.handleTaskUpdates(application.tasks, tasks);
            addTasksToApplication(application, tasks);
          });
        }

        application.registerAutoRefreshHandler = registerAutoRefreshHandler;
        application.autoRefreshHandlers = [];
        application.refreshImmediately = refreshApplication;
        application.disableAutoRefresh = disableAutoRefresh;
        application.enableAutoRefresh = enableAutoRefresh;
        application.getCluster = getCluster;
        application.reloadTasks = reloadTasks;

        if (application.fromServer && application.clusters) {
          application.accounts = Object.keys(application.clusters);
        }
        return application;

      });
    });

    function getApplicationEndpoint(application) {
      return gateEndpoint.one('applications', application);
    }

    function addTasksToApplication(application, tasks) {
      application.tasks = angular.isArray(tasks) ? tasks : [];
      clusterService.addTasksToServerGroups(application);
    }

    function deepCopyApplication(original, newApplication) {
      // tasks are handled out of band and will not be part of the newApplication
      original.accounts = newApplication.accounts;
      original.clusters = newApplication.clusters;
      original.serverGroups = newApplication.serverGroups;
      original.loadBalancers = newApplication.loadBalancers;
      original.securityGroups = newApplication.securityGroups;
      clusterService.addTasksToServerGroups(original);

      newApplication.accounts = null;
      newApplication.clusters = null;
      newApplication.loadBalancers = null;
      newApplication.securityGroups = null;
    }

    function getApplication(applicationName, options) {
      var securityGroupsByApplicationNameLoader = securityGroupService.loadSecurityGroupsByApplicationName(applicationName),
        loadBalancersFromSearch = loadBalancerReader.loadLoadBalancersByApplicationName(applicationName),
        applicationLoader = getApplicationEndpoint(applicationName).get(),
        serverGroupLoader = clusterService.loadServerGroups(applicationName);

      var application, securityGroupAccounts, loadBalancerAccounts, serverGroups;

      var securityGroupLoader;

      return $q.all({
        securityGroups: securityGroupsByApplicationNameLoader,
        loadBalancersFromSearch: loadBalancersFromSearch,
        application: applicationLoader
      })
        .then(function(applicationLoader) {
          application = applicationLoader.application;
          securityGroupAccounts = _(applicationLoader.securityGroups).pluck('account').unique().value();
          loadBalancerAccounts = _(applicationLoader.loadBalancersFromSearch).pluck('account').unique().value();
          application.accounts = _([applicationLoader.application.accounts, securityGroupAccounts, loadBalancerAccounts])
            .flatten()
            .compact()
            .unique()
            .value();

          if (options && options.tasks) {
            tasksReader.listAllTasksForApplication(applicationName).then(function(tasks) {
              addTasksToApplication(application, tasks);
            });
          }

          securityGroupLoader = securityGroupService.loadSecurityGroups(application);

          return $q.all({
            serverGroups: serverGroupLoader,
            securityGroups: securityGroupLoader,
          })
            .then(function(results) {
              serverGroups = results.serverGroups.plain();
              application.serverGroups = serverGroups;
              application.clusters = clusterService.createServerGroupClusters(serverGroups);

              application.loadBalancers = loadBalancerReader.loadLoadBalancers(application, applicationLoader.loadBalancersFromSearch);
              loadBalancerService.normalizeLoadBalancersWithServerGroups(application);
              clusterService.normalizeServerGroupsWithLoadBalancers(application);
              // If the tasks were loaded already, add them to the server groups
              if (application.tasks) {
                clusterService.addTasksToServerGroups(application);
              }
              securityGroupService.attachSecurityGroups(application, results.securityGroups, applicationLoader.securityGroups);

              return application;
            }, function(err) {
              $exceptionHandler(err, 'Failed to load application');
            });
        });
    }


    return {
      listApplications: listApplications,
      getApplication: getApplication,
      getApplicationWithoutAppendages: getApplicationEndpoint,
    };
  });
