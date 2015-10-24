'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.applications.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../../cluster/cluster.service.js'),
    require('../../task/task.read.service.js'),
    require('../../loadBalancer/loadBalancer.read.service.js'),
    require('../../loadBalancer/loadBalancer.transformer.js'),
    require('../../securityGroup/securityGroup.read.service.js'),
    require('../../cache/infrastructureCaches.js'),
    require('../../scheduler/scheduler.service.js'),
    require('../../delivery/service/execution.service.js'),
    require('../../serverGroup/serverGroup.transformer.js'),
    require('../../pipeline/config/services/pipelineConfigService.js'),
  ])
  .factory('applicationReader', function ($q, $log, $window,  $rootScope, Restangular, _, clusterService, taskReader,
                                          loadBalancerReader, loadBalancerTransformer, securityGroupReader, scheduler,
                                          pipelineConfigService,
                                          infrastructureCaches, settings, executionService, serverGroupTransformer) {

    function listApplications() {
      return Restangular
        .all('applications')
        .withHttpConfig({cache: true})
        .getList();
    }

    var gateEndpoint = Restangular.withConfig(function(RestangularConfigurer) {

      RestangularConfigurer.addElementTransformer('applications', false, function(application) {

        function refreshApplication() {
          application.refreshing = true;
          application.reloadTasks();
          application.reloadExecutions();
          return getApplication(application.name).then(function (newApplication) {
            if (newApplication) {
              deepCopyApplication(application, newApplication);
              application.autoRefreshHandlers.forEach((handler) => handler.call());
              application.oneTimeRefreshHandlers.forEach((handler) => handler.call());
              application.oneTimeRefreshHandlers = [];
              newApplication = null;
            }
            application.refreshing = false;
          });
        }


        function deregisterAutoRefreshHandler(method) {
          application.autoRefreshHandlers = application.autoRefreshHandlers.filter((handler) => handler !== method);
        }

        function registerOneTimeRefreshHandler(handler) {
          application.oneTimeRefreshHandlers.push(handler);
        }

        function registerAutoRefreshHandler(method, scope) {
          if (scope.$$destroyed) {
            return;
          }
          application.autoRefreshHandlers.push(method);
          scope.$on('$destroy', function () {
            application.autoRefreshHandlers = application.autoRefreshHandlers.filter((handler) => handler !== method);
          });
        }

        function enableAutoRefresh(scope) {
          let dataLoader = scheduler.subscribe(refreshApplication);
          scope.$on('$destroy', () => dataLoader.dispose());
        }

        function reloadTasks() {
          return taskReader.listAllTasksForApplication(application.name)
            .then(function(tasks) {
              addTasksToApplication(application, tasks);
              if (!application.tasksLoaded) {
                application.tasksLoaded = true;
                $rootScope.$broadcast('tasks-loaded', application);
              } else {
                $rootScope.$broadcast('tasks-reloaded', application);
              }
            })
            .catch(function(rejection) {
              // Gate will send back a 429 error code (TOO_MANY_REQUESTS) and will be caught here
              // As a quick fix we are just adding an empty list to of tasks to the
              // application, which will let the user know that no tasks where found for the app.
              addTasksToApplication(application, []);
              $log.warn('Error retrieving [tasks]', rejection);
            });
        }

        function reloadExecutions() {
          application.executionsLoading = true;
          return executionService.getAll(application.name)
            .then(function(executions) {
              executionService.transformExecutions(application, executions);
              addExecutionsToApplication(application, executions);
              if (!application.executionsLoaded) {
                application.executionsLoading = false;
                application.executionsLoaded = true;
                $rootScope.$broadcast('executions-loaded', application);
              } else {
                $rootScope.$broadcast('executions-reloaded', application);
              }
            })
            .catch(function(rejection) {
              // Gate will send back a 429 error code (TOO_MANY_REQUESTS) and will be caught here.
              $log.warn('Error retrieving [executions]', rejection);
              $rootScope.$broadcast('executions-load-failure', application);
            });
        }

        function reloadPipelineConfigs() {
          application.pipelineConfigsLoading = true;
          return pipelineConfigService.getPipelinesForApplication(application.name)
            .then((configs) => {
              application.pipelineConfigs = configs;
              application.pipelineConfigsLoading = false;
              $rootScope.$broadcast('pipelineConfigs-loaded', application);
            });
        }


        application.registerAutoRefreshHandler = registerAutoRefreshHandler;
        application.deregisterAutoRefreshHandler = deregisterAutoRefreshHandler;
        application.registerOneTimeRefreshHandler = registerOneTimeRefreshHandler;
        application.autoRefreshHandlers = [];
        application.oneTimeRefreshHandlers = [];
        application.refreshImmediately = scheduler.scheduleImmediate;
        application.enableAutoRefresh = enableAutoRefresh;
        application.reloadTasks = reloadTasks;
        application.reloadExecutions = reloadExecutions;
        application.reloadPipelineConfigs = reloadPipelineConfigs;

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

    function addExecutionsToApplication(application, executions=[]) {
      // only add executions if we actually got some executions back
      // this will fail if there was just one execution and someone just deleted it
      // but that is much less likely at this point than orca falling over under load,
      // resulting in an empty list of executions coming back
      if (application.executions && application.executions.length && executions.length) {

        // remove any that have dropped off, update any that have changed
        let toRemove = [];
        application.executions.forEach((execution, idx) => {
          let matches = executions.filter((test) => test.id === execution.id);
          if (!matches.length) {
            toRemove.push(idx);
          } else {
            if (execution.stringVal && matches[0].stringVal && execution.stringVal !== matches[0].stringVal) {
              application.executions[idx] = matches[0];
            }
          }
        });

        toRemove.forEach((idx) => application.executions.splice(idx, 1));

        // add any new ones
        executions.forEach((execution) => {
          if (!application.executions.filter((test) => test.id === execution.id).length) {
            application.executions.push(execution);
          }
        });
      } else {
        application.executions = executions;
      }
      clusterService.addExecutionsToServerGroups(application);
    }

    function deepCopyApplication(original, newApplication) {
      // tasks are handled out of band and will not be part of the newApplication
      original.accounts = newApplication.accounts;
      original.clusters = newApplication.clusters;
      applyNewServerGroups(original, newApplication.serverGroups);
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
        serverGroupLoader = clusterService.loadServerGroups(applicationName),
        executionsLoader = options && options.executions ? executionService.getAll(applicationName) : $q.when(null);

      var application, securityGroupAccounts, loadBalancerAccounts, serverGroups;

      var securityGroupLoader;

      return $q.all({
        securityGroups: securityGroupsByApplicationNameLoader,
        loadBalancers: loadBalancerLoader,
        application: applicationLoader,
        executions: executionsLoader,
      })
        .then(function(applicationLoader) {
          application = applicationLoader.application;

          // These attributes are stored as strings.
          application.attributes.platformHealthOnly = (application.attributes.platformHealthOnly === 'true');
          application.attributes.platformHealthOnlyShowOverride = (application.attributes.platformHealthOnlyShowOverride === 'true');

          application.executionsLoading = false;

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
            executionService.transformExecutions(application, applicationLoader.executions);
            addExecutionsToApplication(application, applicationLoader.executions);
            application.executionsLoaded = true;
          }

          if (options && options.pipelineConfigs) {
            application.reloadPipelineConfigs();
          }

          securityGroupLoader = securityGroupReader.loadSecurityGroups(application);

          return $q.all({
            serverGroups: serverGroupLoader,
            securityGroups: securityGroupLoader,
          })
            .then(function(results) {
              serverGroups = results.serverGroups;
              serverGroups.forEach((serverGroup) => serverGroup.stringVal = JSON.stringify(serverGroup, serverGroupTransformer.jsonReplacer));
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
                    addDefaultRegion(application);
                    addDefaultCredentials(application);
                    return application;
                  },
                  function(err) {
                    $log.error(err, 'Failed to load application');
                  }
                );
            }, function(err) {
              $log.error(err, 'Failed to load application');
            });
        });
    }

    function applyNewServerGroups(application, serverGroups) {
      if (application.serverGroups) {
        // remove any that have dropped off, update any that have changed
        let toRemove = [];
        application.serverGroups.forEach((serverGroup, idx) => {
          let matches = serverGroups.filter((test) =>
            test.name === serverGroup.name && test.account === serverGroup.account && test.region === serverGroup.region
          );
          if (!matches.length) {
            toRemove.push(idx);
          } else {
            if (serverGroup.stringVal && matches[0].stringVal && serverGroup.stringVal !== matches[0].stringVal) {
              application.serverGroups[idx] = matches[0];
            }
          }
        });

        toRemove.forEach((idx) => application.serverGroups.splice(idx, 1));

        // add any new ones
        serverGroups.forEach((serverGroup) => {
          if (!application.serverGroups.filter((test) =>
            test.name === serverGroup.name && test.account === serverGroup.account && test.region === serverGroup.region
            ).length) {
            application.serverGroups.push(serverGroup);
          }
        });
      } else {
        application.serverGroups = serverGroups;
      }
    }

    return {
      listApplications: listApplications,
      getApplication: getApplication,
      getApplicationWithoutAppendages: getApplicationEndpoint,
      addExecutionsToApplication: addExecutionsToApplication,
    };
  }).name;
