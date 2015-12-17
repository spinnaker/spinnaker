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
    require('../../utils/rx.js'),
    require('../../utils/lodash.js'),
  ])
  .factory('applicationReader', function ($q, $log, $window,  $rootScope, Restangular, _, clusterService, taskReader,
                                          loadBalancerReader, loadBalancerTransformer, securityGroupReader, scheduler,
                                          pipelineConfigService, rx,
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
          if (application.refreshing) {
            $log.warn('application still reloading, skipping reload');
            return $q.when(null);
          }
          application.refreshing = true;
          application.reloadTasks();
          application.reloadExecutions();
          return getApplication(application.name).then(function (newApplication) {
            if (newApplication && !newApplication.notFound) {
              deepCopyApplication(application, newApplication);
              application.autoRefreshStream.onNext();
              application.autoRefreshHandlers.forEach((handler) => handler.call());
              application.oneTimeRefreshHandlers.forEach((handler) => handler.call());
              application.oneTimeRefreshHandlers = [];
              newApplication = null;
            }
            application.refreshing = false;
          })
          .catch(function(rejection) {
              $log.warn('Error refreshing application:', rejection);
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

        function reloadTasks(forceReload) {
          if (application.tasksLoading && !forceReload) {
            $log.warn('tasks still loading, skipping reload');
            return $q.when(null);
          }
          application.tasksLoading = true;
          let taskLoader = application.loadAllTasks ?
            taskReader.getTasks(application.name) :
            taskReader.getRunningTasks(application.name);
          return taskLoader
            .then(function(tasks) {
              addTasksToApplication(application, tasks);
              application.taskRefreshStream.onNext();
              application.tasksLoaded = true;
              application.tasksLoading = false;
              application.tasksLoadFailure = false;
            })
            .catch(function(rejection) {
              // Gate will send back a 429 error code (TOO_MANY_REQUESTS) and will be caught here
              // As a quick fix we are just adding an empty list to the
              // application, which will let the user know that no tasks were found for the app.
              addTasksToApplication(application, []);
              $log.warn('Error retrieving [tasks]', rejection);
              application.tasksLoading = false;
              application.tasksLoadFailure = true;
              application.taskRefreshStream.onNext(rejection);
            });
        }

        function reloadExecutions(forceReload) {
          if (application.executionsLoading && !forceReload) {
            $log.warn('executions still loading, skipping reload');
            return $q.when(null);
          }
          application.executionsLoading = true;
          let executionLoader = application.loadAllExecutions ?
            executionService.getExecutions(application.name) :
            executionService.getRunningExecutions(application.name);
          return executionLoader
            .then(function(executions) {
              executionService.transformExecutions(application, executions);
              addExecutionsToApplication(application, executions);
              application.executionsLoaded = true;
              application.executionsLoading = false;
              application.executionsLoadFailure = false;
              application.executionRefreshStream.onNext();
            })
            .catch(function(rejection) {
              // Gate will send back a 429 error code (TOO_MANY_REQUESTS) and will be caught here.
              $log.warn('Error retrieving [executions]', rejection);
              application.executionsLoading = false;
              application.executionsLoadFailure = true;
              application.executionRefreshStream.onNext(rejection);
            });
        }

        function reloadPipelineConfigs() {
          if (application.pipelineConfigsLoading) {
            $log.warn('pipeline configs still loading, skipping reload');
            return $q.when(null);
          }
          application.pipelineConfigsLoading = true;
          let pipelineLoader = pipelineConfigService.getPipelinesForApplication(application.name),
              strategyLoader = pipelineConfigService.getStrategiesForApplication(application.name);
          return $q.all({pipelines: pipelineLoader, strategies: strategyLoader})
            .then((configs) => {
              application.pipelineConfigs = configs.pipelines;
              application.strategyConfigs = configs.strategies;
              application.pipelineConfigsLoaded = true;
              application.pipelineConfigsLoading = false;
              application.pipelineConfigsLoadFailure = false;
              application.pipelineConfigRefreshStream.onNext();
            }).catch(function(rejection) {
              $log.warn('Error retrieving [pipelineConfigs]', rejection);
              application.pipelineConfigsLoading = false;
              application.pipelineConfigsLoadFailure = true;
              application.pipelineConfigRefreshStream.onNext();
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
        application.autoRefreshStream = new rx.Subject();
        application.taskRefreshStream = new rx.Subject();
        application.executionRefreshStream = new rx.Subject();
        application.pipelineConfigRefreshStream = new rx.Subject();
        return application;
      });
    });

    function getApplicationEndpoint(application) {
      return gateEndpoint.one('applications', application);
    }

    function addDefaultRegion(application) {
      application.defaultRegions = {};
      var serverGroupProviders = _.pluck(application.serverGroups, 'provider'),
          loadBalancerProviders = _.pluck(application.loadBalancers, 'type'),
          securityGroupProviders = _.pluck(application.securityGroups, 'type');

      var allProviders = _.union(serverGroupProviders, loadBalancerProviders, securityGroupProviders);
      allProviders.forEach((provider) => {
        var fromServerGroups = _.pluck(_.filter(application.serverGroups, {provider: provider}), 'region'),
            fromLoadBalancers = _.pluck(_.filter(application.loadBalancers, {type: provider}), 'region'),
            fromSecurityGroups = _.pluck(_.filter(application.securityGroups, {type: provider}), 'region');
        var allRegions = _.union(fromServerGroups, fromLoadBalancers, fromSecurityGroups);
        if (allRegions.length === 1) {
          application.defaultRegions[provider] = allRegions[0];
        }
      });
    }

    function addDefaultCredentials(application) {
      application.defaultCredentials = {};
      var serverGroupProviders = _.pluck(application.serverGroups, 'provider'),
          loadBalancerProviders = _.pluck(application.loadBalancers, 'type'),
          securityGroupProviders = _.pluck(application.securityGroups, 'type');

      var allProviders = _.union(serverGroupProviders, loadBalancerProviders, securityGroupProviders);
      allProviders.forEach((provider) => {
        var fromServerGroups = _.pluck(_.filter(application.serverGroups, {provider: provider}), 'account'),
            fromLoadBalancers = _.pluck(_.filter(application.loadBalancers, {type: provider}), 'account'),
            fromSecurityGroups = _.pluck(_.filter(application.securityGroups, {type: provider}), 'accountName');
        var allRegions = _.union(fromServerGroups, fromLoadBalancers, fromSecurityGroups);
        if (allRegions.length === 1) {
          application.defaultCredentials[provider] = allRegions[0];
        }
      });
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

        toRemove.reverse().forEach((idx) => application.executions.splice(idx, 1));

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
      original.defaultRegions = newApplication.defaultRegions;
      original.defaultCredentials = newApplication.defaultCredentials;

      clusterService.addTasksToServerGroups(original);
      clusterService.addExecutionsToServerGroups(original);

      newApplication.accounts = null;
      newApplication.clusters = null;
      newApplication.loadBalancers = null;
      newApplication.securityGroups = null;
    }

    function getApplicationLoaders(applicationName, options) {
      var securityGroupsByApplicationNameLoader = securityGroupReader.loadSecurityGroupsByApplicationName(applicationName),
          loadBalancerLoader = loadBalancerReader.loadLoadBalancers(applicationName),
          applicationLoader = getApplicationEndpoint(applicationName).get(),
          serverGroupLoader = clusterService.loadServerGroups(applicationName),
          executionsLoader = options && options.executions ?
            options.loadAllExecutions ?
              executionService.getExecutions(applicationName) :
              executionService.getRunningExecutions(applicationName) :
            $q.when(null),
          tasksLoader = options && options.tasks ?
            options.loadAllTasks ?
              taskReader.getTasks(applicationName) :
              taskReader.getRunningTasks(applicationName) :
            $q.when(null);

      return {
        securityGroups: securityGroupsByApplicationNameLoader,
        serverGroups: serverGroupLoader,
        executions: executionsLoader,
        tasks: tasksLoader,
        loadBalancers: loadBalancerLoader,
        application: applicationLoader,
      };
    }

    function applyExecutionsAndTasks(options, dataLoaders, application) {
      if (options && options.tasks) {
        dataLoaders.tasks.then(
          (tasks) => {
            addTasksToApplication(application, tasks);
            application.tasksLoading = false;
            application.tasksLoaded = true;
            application.tasksLoadFailure = false;
          },
          () => {
            application.tasksLoaded = false;
            application.tasksLoading = false;
            application.tasksLoadFailure = true;
          }
        );
      }

      if (options && options.executions) {
        dataLoaders.executions.then(
          (executions) => {
            executionService.transformExecutions(application, executions);
            addExecutionsToApplication(application, executions);
            application.executionsLoaded = true;
            application.executionsLoadFailure = false;
            application.executionsLoading = false;
          },
          () => {
            application.executionsLoaded = false;
            application.executionsLoadFailure = true;
            application.executionsLoading = false;
          }
        );
      }
    }

    function setApplicationAccounts(applicationData) {
      var application = applicationData.application,
          securityGroupAccounts = _(applicationData.securityGroups).pluck('account').unique().value(),
          loadBalancerAccounts = _(applicationData.loadBalancers).pluck('account').unique().value();

      application.accounts = _([applicationData.application.accounts, securityGroupAccounts, loadBalancerAccounts])
        .flatten()
        .compact()
        .unique()
        .value();
    }

    function getApplication(applicationName, options) {
      let dataSources = getApplicationLoaders(applicationName, options);

      return $q.all({
        securityGroups: dataSources.securityGroups,
        loadBalancers: dataSources.loadBalancers,
        application: dataSources.application,
      })
        .then(function(applicationData) {
          var application = applicationData.application,
              securityGroupLoader = securityGroupReader.loadSecurityGroups(application);

          setApplicationAccounts(applicationData);

          if (options && options.pipelineConfigs) {
            application.reloadPipelineConfigs();
          }

          return $q.all({
            serverGroups: dataSources.serverGroups,
            securityGroups: securityGroupLoader,
          })
            .then(function(results) {
              let serverGroups = results.serverGroups;
              serverGroups.forEach((serverGroup) => serverGroup.stringVal = JSON.stringify(serverGroup, serverGroupTransformer.jsonReplacer));
              application.serverGroups = serverGroups;
              application.clusters = clusterService.createServerGroupClusters(serverGroups);
              application.loadBalancers = applicationData.loadBalancers;

              clusterService.normalizeServerGroupsWithLoadBalancers(application);
              applyExecutionsAndTasks(options, dataSources, application);
              return securityGroupReader.attachSecurityGroups(application, results.securityGroups, applicationData.securityGroups, true)
                .then(() => applicationLoadSuccess(application), applicationLoadError);
            }, applicationLoadError);
        }, applicationLoadError);
    }

    function applicationLoadSuccess(application) {
      addDefaultRegion(application);
      addDefaultCredentials(application);
      application.lastRefresh = new Date().getTime();
      return application;
    }

    function applicationLoadError(err) {
      $log.error(err, 'Failed to load application, will retry on next scheduler execution.');
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
  });
