'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.applications.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../../services/clusterService.js'),
    require('../tasks/tasks.read.service.js'),
    require('../loadBalancers/loadBalancer.read.service.js'),
    require('../loadBalancers/loadBalancer.transformer.js'),
    require('../securityGroups/securityGroup.read.service.js'),
    require('../caches/infrastructureCaches.js'),
    require('../scheduler/scheduler.service.js'),
    require('../delivery/executionsService.js'),
  ])
  .factory('applicationReader', function ($q, $log, $window,  $exceptionHandler, $rootScope, Restangular, _, clusterService, tasksReader,
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
          $window.addEventListener('offline', suspendAutoRefresh);
          $window.addEventListener('online', resumeAutoRefresh);
          application.autoRefreshEnabled = true;
          autoRefresh(scope);
        }

        function reloadTasks() {
          return tasksReader.listAllTasksForApplication(application.name).then(function(tasks) {
            addTasksToApplication(application, tasks);
            if (!application.tasksLoaded) {
              application.tasksLoaded = true;
              $rootScope.$broadcast('tasks-loaded', application);
            } else {
              $rootScope.$broadcast('tasks-reloaded', application);
            }
          });
        }

        function reloadExecutions() {
          return executionsService.getAll(application).then(function(execution) {
            addExecutionsToApplication(application, execution);
            if (!application.executionsLoaded) {
              application.executionsLoaded = true;
              $rootScope.$broadcast('executions-loaded', application);
            } else {
              $rootScope.$broadcast('executions-reloaded', application);
            }
          });
        }


        application.registerAutoRefreshHandler = registerAutoRefreshHandler;
        application.autoRefreshHandlers = [];
        application.refreshImmediately = refreshApplication;
        application.disableAutoRefresh = disableAutoRefresh;
        application.enableAutoRefresh = enableAutoRefresh;
        application.resumeAutoRefresh = resumeAutoRefresh;
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

    function addDefaultRegion(application) {
      var fromServerGroups = _.pluck(application.serverGroups, 'region'),
        fromLoadBalancers = _.pluck(application.loadBalancers, 'region'),
        fromSecurityGroups = _.pluck(application.securityGroups, 'region');

      var allRegions = _.union(fromServerGroups, fromLoadBalancers, fromSecurityGroups);

      if (allRegions.length === 1) {
        application.defaultRegion = allRegions[0];
      }
    }

    function addDefaultCredentials(application) {
      var fromServerGroups = _.pluck(application.serverGroups, 'account'),
        fromLoadBalancers = _.pluck(application.loadBalancers, 'account'),
        fromSecurityGroups = _.pluck(application.securityGroups, 'accountName');

      var allCredentials = _.union(fromServerGroups, fromLoadBalancers, fromSecurityGroups);

      if (allCredentials.length === 1) {
        application.defaultCredentials = allCredentials[0];
      }
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
      original.securityGroupsIndex = newApplication.securityGroupsIndex;
      original.defaultRegion = newApplication.defaultRegion;
      original.defaultCredentials = newApplication.defaultCredentials;

      clusterService.addTasksToServerGroups(original);
      clusterService.addExecutionsToServerGroups(original);

      newApplication.accounts = null;
      newApplication.clusters = null;
      newApplication.loadBalancers = null;
      newApplication.securityGroups = null;
    }

    function getApplication(applicationName, options) {
      var securityGroupsByApplicationNameLoader = securityGroupReader.loadSecurityGroupsByApplicationName(applicationName),
        loadBalancerLoader = loadBalancerReader.loadLoadBalancers(applicationName),
        applicationLoader = getApplicationEndpoint(applicationName).get(),
        serverGroupLoader = clusterService.loadServerGroups(applicationName);

      var application, securityGroupAccounts, loadBalancerAccounts, serverGroups;

      var securityGroupLoader;

      return $q.all({
        securityGroups: securityGroupsByApplicationNameLoader,
        loadBalancers: loadBalancerLoader,
        application: applicationLoader
      })
        .then(function(applicationLoader) {
          application = applicationLoader.application;
          application.lastRefresh = new Date().getTime();
          securityGroupAccounts = _(applicationLoader.securityGroups).pluck('account').unique().value();
          loadBalancerAccounts = _(applicationLoader.loadBalancers).pluck('account').unique().value();
          application.accounts = _([applicationLoader.application.accounts, securityGroupAccounts, loadBalancerAccounts])
            .flatten()
            .compact()
            .unique()
            .value();

          if (options && options.tasks) {
            application.reloadTasks();
          }

          if (options && options.executions) {
            application.reloadExecutions();
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
              application.loadBalancers = applicationLoader.loadBalancers;

              clusterService.normalizeServerGroupsWithLoadBalancers(application);
              // If the tasks were loaded already, add them to the server groups
              if (application.tasks) {
                clusterService.addTasksToServerGroups(application);
              }
              return securityGroupReader.attachSecurityGroups(application, results.securityGroups, applicationLoader.securityGroups, true)
                .then(
                  function() {
                    application.serverGroups.forEach(function(sg) {
                      sg.stringVal = JSON.stringify(sg, jsonReplacer);
                    });
                    addDefaultRegion(application);
                    addDefaultCredentials(application);
                    return application;
                  },
                  function(err) {
                    $exceptionHandler(err, 'Failed to load application');
                  }
                );
            }, function(err) {
              $exceptionHandler(err, 'Failed to load application');
            });
        });
    }

    // strips out Angular bits (see angular.js#toJsonReplacer), as well as executions, which often
    // have circular references because of the horrible things we do in executions.transformer.js
    function jsonReplacer(key, value) {
      var val = value;

      if (typeof key === 'string' && key.charAt(0) === '$' && key.charAt(1) === '$') {
        val = undefined;
      }

      if (key === 'executions') {
        val = undefined;
      }

      return val;
    }



    return {
      listApplications: listApplications,
      getApplication: getApplication,
      getApplicationWithoutAppendages: getApplicationEndpoint,
    };
  }).name;
