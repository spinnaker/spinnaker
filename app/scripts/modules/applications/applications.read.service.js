'use strict';

angular
  .module('deckApp.applications.read.service', [
    'restangular',
    'deckApp.cluster.service',
    'deckApp.tasks.tracker',
    'deckApp.tasks.read.service',
    'deckApp.loadBalancer.read.service',
    'deckApp.loadBalancer.transformer.service',
    'deckApp.securityGroup.read.service',
    'deckApp.caches.infrastructure',
    'deckApp.scheduler',
    'deckApp.delivery.executions.service',
  ])
  .factory('applicationReader', function ($q,$log, $window,  $exceptionHandler, Restangular, _, clusterService, taskTracker, tasksReader,
                                          loadBalancerReader, loadBalancerTransformer, securityGroupReader, scheduler,
                                          infrastructureCaches, settings, executionsService) {

    function listApplications(forceRemoteCall) {
      var endpoint = Restangular
        .all('applications')
        .withHttpConfig({cache: infrastructureCaches.applications});

      if (forceRemoteCall) {
        infrastructureCaches.applications.remove(endpoint.getRestangularUrl());
      }

      return endpoint.getList();
    }



    var gateEndpoint = Restangular.withConfig(function(RestangularConfigurer) {

      RestangularConfigurer.addElementTransformer('applications', false, function(application) {

        function refreshApplication(forceRefresh) {
          if (application.autoRefreshEnabled || forceRefresh) {
            application.refreshing = true;
            application.reloadTasks();
            application.reloadExecutions();
            return getApplication(application.name).then(function (newApplication) {
              deepCopyApplication(application, newApplication);
              application.autoRefreshHandlers.forEach(function (handler) {
                handler.call();
              });
              newApplication = null;
              application.refreshing = false;
            });
          }
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
          document.removeEventListener('visibilitychange', watchDocumentVisibility);
          $window.removeEventListener('blur', suspendAutoRefresh);
          $window.removeEventListener('focus', resumeAutoRefresh);
        }

        function suspendAutoRefresh() {
          $log.debug('auto refresh suspended');
          application.autoRefreshEnabled = false;
        }

        function resumeAutoRefresh() {
          application.autoRefreshEnabled = true;
          $log.debug('auto refresh resumed');
          var now = new Date().getTime();
          if (application.lastRefresh && now - application.lastRefresh > settings.pollSchedule) {
            $log.debug('scheduling immediate refresh, last refresh was', now - application.lastRefresh, 'ms ago');
            scheduler.scheduleImmediate(refreshApplication);
          }
        }

        function watchDocumentVisibility() {
          $log.debug('document visibilityState changed to: ', document.visibilityState);
          if (document.visibilityState === 'visible') {
            resumeAutoRefresh();
          } else {
            suspendAutoRefresh();
          }
        }

        function enableAutoRefresh (scope) {
          document.addEventListener('visibilitychange', watchDocumentVisibility);
          $window.addEventListener('blur', suspendAutoRefresh);
          $window.addEventListener('offline', suspendAutoRefresh);
          $window.addEventListener('online', resumeAutoRefresh);
          $window.addEventListener('focus', resumeAutoRefresh);
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

        function reloadExecutions() {
          executionsService.getAll(application.name).then(function(execution) {
            addExecutionsToApplication(application, execution);
          });
        }


        application.registerAutoRefreshHandler = registerAutoRefreshHandler;
        application.autoRefreshHandlers = [];
        application.refreshImmediately = refreshApplication;
        application.disableAutoRefresh = disableAutoRefresh;
        application.enableAutoRefresh = enableAutoRefresh;
        application.getCluster = getCluster;
        application.reloadTasks = reloadTasks;
        application.reloadExecutions = reloadExecutions;

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

    function addExecutionsToApplication(application, executions) {
      application.executions = angular.isArray(executions) ? executions : [];
      clusterService.addExecutionsToServerGroups(application);
    }

    function deepCopyApplication(original, newApplication) {
      // tasks are handled out of band and will not be part of the newApplication
      original.accounts = newApplication.accounts;
      original.clusters = newApplication.clusters;
      original.serverGroups = newApplication.serverGroups;
      original.loadBalancers = newApplication.loadBalancers;
      original.securityGroups = newApplication.securityGroups;
      original.lastRefresh = newApplication.lastRefresh;
      clusterService.addTasksToServerGroups(original);
      clusterService.addExecutionsToServerGroups(original);

      newApplication.accounts = null;
      newApplication.clusters = null;
      newApplication.loadBalancers = null;
      newApplication.securityGroups = null;
    }

    function getApplication(applicationName, options) {
      var securityGroupsByApplicationNameLoader = securityGroupReader.loadSecurityGroupsByApplicationName(applicationName),
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
          application.lastRefresh = new Date().getTime();
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

          securityGroupLoader = securityGroupReader.loadSecurityGroups(application);

          return $q.all({
            serverGroups: serverGroupLoader,
            securityGroups: securityGroupLoader,
          })
            .then(function(results) {
              serverGroups = results.serverGroups.plain();
              application.serverGroups = serverGroups;
              application.clusters = clusterService.createServerGroupClusters(serverGroups);
              application.loadBalancers = loadBalancerReader.loadLoadBalancers(application, applicationLoader.loadBalancersFromSearch);

              if(options && options.executions) {
                executionsService.getAll(applicationName).then(function (executions) {
                  addExecutionsToApplication(application, executions);
                });
              }


              loadBalancerTransformer.normalizeLoadBalancersWithServerGroups(application);
              clusterService.normalizeServerGroupsWithLoadBalancers(application);
              // If the tasks were loaded already, add them to the server groups
              if (application.tasks) {
                clusterService.addTasksToServerGroups(application);
              }
              securityGroupReader.attachSecurityGroups(application, results.securityGroups, applicationLoader.securityGroups);

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
