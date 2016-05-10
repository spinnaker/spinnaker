'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.applications.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../../cluster/cluster.service.js'),
    require('../../task/task.read.service.js'),
    require('../../loadBalancer/loadBalancer.read.service.js'),
    require('../../securityGroup/securityGroup.read.service.js'),
    require('../../scheduler/scheduler.factory.js'),
    require('../../delivery/service/execution.service.js'),
    require('../../serverGroup/serverGroup.transformer.js'),
    require('../../pipeline/config/services/pipelineConfigService.js'),
    require('../../utils/rx.js'),
    require('../../utils/lodash.js'),
  ])
  .factory('applicationReader', function ($q, $log, Restangular, _, rx, $http, settings, $location,
                                            clusterService, taskReader, loadBalancerReader, securityGroupReader,
                                            schedulerFactory, pipelineConfigService, executionService,
                                            serverGroupTransformer) {

    function listApplications() {
      return Restangular
        .all('applications')
        .withHttpConfig({cache: true})
        .getList();
    }

    let addTasks = (application, tasks) => {
      application.tasks.data = angular.isArray(tasks) ? tasks : [];
      return $q.when(null);
    };

    let loadTasks = (application) => {
      return taskReader.getTasks(application.name);
    };

    let addExecutions = (application, executions) => {
      executionService.transformExecutions(application, executions);
      executionService.addExecutionsToApplication(application, executions);
      return $q.when(null);
    };

    let loadExecutions = (application) => {
      return executionService.getExecutions(application.name);
    };

    let loadLoadBalancers = (application) => {
      return loadBalancerReader.loadLoadBalancers(application.name);
    };

    let addLoadBalancers = (application, loadBalancers) => {
      application.loadBalancers.data = loadBalancers;
      securityGroupReader.attachSecurityGroups(application, null, true);
      return $q.when(null);
    };

    let loadServerGroups = (application) => {
      return clusterService.loadServerGroups(application.name);
    };

    let addServerGroups = (application, serverGroups) => {
      serverGroups.forEach(serverGroup => serverGroup.stringVal = JSON.stringify(serverGroup, serverGroupTransformer.jsonReplacer));
      application.clusters = clusterService.createServerGroupClusters(serverGroups);
      clusterService.addServerGroupsToApplication(application, serverGroups);
      clusterService.addTasksToServerGroups(application);
      clusterService.addExecutionsToServerGroups(application);
      securityGroupReader.attachSecurityGroups(application, null, true);
      return $q.when(null);
    };

    let loadPipelineConfigs = (application) => {
      let pipelineLoader = pipelineConfigService.getPipelinesForApplication(application.name),
          strategyLoader = pipelineConfigService.getStrategiesForApplication(application.name);
      return $q.all({pipelineConfigs: pipelineLoader, strategyConfigs: strategyLoader});
    };

    let addPipelineConfigs = (application, data) => {
      application.pipelineConfigs.data = data.pipelineConfigs;
      application.strategyConfigs = { data: data.strategyConfigs };
      return $q.when(null);
    };

    let loadSecurityGroups = (application) => {
      return securityGroupReader.loadSecurityGroupsByApplicationName(application.name);
    };

    let addSecurityGroups = (application, securityGroups) => {
      return securityGroupReader.loadSecurityGroups().then((allSecurityGroups) => {
        application.securityGroupsIndex = allSecurityGroups;
        securityGroupReader.attachSecurityGroups(application, securityGroups, true);
      });
    };

    let loadRunningOrchestrations = (application) => {
      if (!application.runningTasks) {
        application.runningTasks = { data: [] };
        application.runningExecutions = { data: [] };
      }
      let executionLoader = executionService.getRunningExecutions(application.name),
          taskLoader = taskReader.getRunningTasks(application.name);
      return $q.all({executions: executionLoader, tasks: taskLoader});
    };

    let addRunningOrchestrations = (application, data) => {
      executionService.transformExecutions(application, data.executions);
      application.runningTasks = {data: data.tasks};
      application.runningExecutions = {data: data.executions};
      clusterService.addExecutionsToServerGroups(application);
      clusterService.addTasksToServerGroups(application);
      return $q.when(null);
    };

    let applicationSections = [
      {
        key: 'serverGroups',
        loader: loadServerGroups,
        onLoad: addServerGroups,
      },
      {
        key: 'tasks',
        loader: loadTasks,
        onLoad: addTasks,
        lazy: true,
      },
      {
        key: 'executions',
        loader: loadExecutions,
        onLoad: addExecutions,
        lazy: true,
      },
      {
        key: 'loadBalancers',
        loader: loadLoadBalancers,
        onLoad: addLoadBalancers,
      },
      {
        key: 'pipelineConfigs',
        loader: loadPipelineConfigs,
        onLoad: addPipelineConfigs,
        lazy: true,
      },
      {
        key: 'securityGroups',
        loader: loadSecurityGroups,
        onLoad: addSecurityGroups,
      },
      {
        key: 'runningOrchestrations',
        loader: loadRunningOrchestrations,
        onLoad: addRunningOrchestrations,
      }
    ];

    function addSectionToApplication(sectionConfig, application) {
      let key = sectionConfig.key;
      let refreshStream = new rx.Subject();
      let refreshFailureStream = new rx.Subject();
      application[key] = {
        loaded: false,
        loading: false,
        loadFailure: false,
        data: [],
        lastRefresh: null,
        refreshStream: refreshStream,
        refreshFailureStream: refreshFailureStream,
        onNextRefresh: function($scope, method, failureMethod) {
          let success = refreshStream.take(1).subscribe(method);
          $scope.$on('$destroy', () => success.dispose());
          if (failureMethod) {
            let failure = refreshFailureStream.take(1).subscribe(failureMethod);
            $scope.$on('$destroy', () => failure.dispose());
          }
        },
        onRefresh: function($scope, method, failureMethod) {
          let success = refreshStream.subscribe(method);
          $scope.$on('$destroy', () => success.dispose());
          if (failureMethod) {
            let failure = refreshFailureStream.subscribe(failureMethod);
            $scope.$on('$destroy', () => failure.dispose());
          }
        },
      };

      let section = application[key];

      section.ready = () => {
        let deferred = $q.defer();
        if (section.loaded) {
          deferred.resolve();
        } else if (section.loadFailure) {
          deferred.reject();
        } else {
          section.refreshStream.take(1).subscribe(deferred.resolve);
          section.refreshFailureStream.take(1).subscribe(deferred.reject);
        }
        return deferred.promise;
      };

      section.activate = () => {
        if (!section.active) {
          section.active = true;
          if (!section.loaded) {
            section.refresh();
          }
        }
      };

      section.deactivate = () => {
        section.active = false;
      };

      section.refresh = (forceRefresh) => {
        if (sectionConfig.lazy && !section.active) {
          section.data.length = 0;
          section.loaded = false;
          return $q.when(null);
        }
        if (section.loading && !forceRefresh) {
          $log.warn(`${key} still loading, skipping refresh`);
          return $q.when(null);
        }
        section.loading = true;
        return sectionConfig.loader(application)
          .then((result) => {
            sectionConfig.onLoad(application, result).then(() => {
              section.loaded = true;
              section.loading = false;
              section.loadFailure = false;
              section.lastRefresh = new Date().getTime();
              section.refreshStream.onNext();
            });
          })
          .catch((rejection) => {
            $log.warn(`Error retrieving ${section.key}`, rejection);
            section.loading = false;
            section.loadFailure = true;
            section.refreshFailureStream.onNext(rejection);
          });
      };
      if (!sectionConfig.lazy) {
        // *probably* only for testing
        application[key].refresh();
      }
    }

    function getApplication(applicationName) {
      let refreshStream = new rx.Subject();
      let refreshFailureStream = new rx.Subject();
      let application = {
        name: applicationName,
        refresh: (forceRefresh) => {
          let toRefresh = applicationSections.map(s => s.key);
          return $q.all(toRefresh.map(section => application[section].refresh(forceRefresh)))
            .then(
            () => applicationLoadSuccess(application),
            (error) => applicationLoadError(error)
          );
        },
        scheduler: schedulerFactory.createScheduler(),
        refreshStream: refreshStream,
        refreshFailureStream: refreshFailureStream,
        onRefresh: function($scope, method, failureMethod) {
          let success = refreshStream.subscribe(method);
          $scope.$on('$destroy', () => success.dispose());
          if (failureMethod) {
            let failure = refreshFailureStream.subscribe(failureMethod);
            $scope.$on('$destroy', () => failure.dispose());
          }
        },
        enableAutoRefresh: (scope) => {
          let dataLoader = application.scheduler.subscribe(() => application.refresh());
          scope.$on('$destroy', () => {
            dataLoader.dispose();
            application.scheduler.dispose();
          });
        }
      };

      applicationSections.forEach((section) => addSectionToApplication(section, application));

      application.ready = () => {
        let sections = applicationSections.map(s => application[s.key]);
        return $q.all(sections.map((section) => section.ready()));
      };

      return $http.get([settings.gateUrl, 'applications', applicationName].join('/'))
        .then((response) => {
          delete response.data.clusters; // do not overwrite the clusters we constructed!
          angular.extend(application, response.data);
          applicationLoadSuccess(application);
          return application;
        });
    }

    function addDefaultRegion(application) {
      application.defaultRegions = {};
      var serverGroupProviders = _.pluck(application.serverGroups.data, 'provider'),
          loadBalancerProviders = _.pluck(application.loadBalancers.data, 'type'),
          securityGroupProviders = _.pluck(application.securityGroups.data, 'type');

      var allProviders = _.union(serverGroupProviders, loadBalancerProviders, securityGroupProviders);
      allProviders.forEach((provider) => {
        var fromServerGroups = _.pluck(_.filter(application.serverGroups.data, {provider: provider}), 'region'),
            fromLoadBalancers = _.pluck(_.filter(application.loadBalancers.data, {type: provider}), 'region'),
            fromSecurityGroups = _.pluck(_.filter(application.securityGroups.data, {type: provider}), 'region');
        var allRegions = _.union(fromServerGroups, fromLoadBalancers, fromSecurityGroups);
        if (allRegions.length === 1) {
          application.defaultRegions[provider] = allRegions[0];
        }
      });
    }

    function addDefaultCredentials(application) {
      application.defaultCredentials = {};
      var serverGroupProviders = _.pluck(application.serverGroups.data, 'provider'),
          loadBalancerProviders = _.pluck(application.loadBalancers.data, 'type'),
          securityGroupProviders = _.pluck(application.securityGroups.data, 'type');

      var allProviders = _.union(serverGroupProviders, loadBalancerProviders, securityGroupProviders);
      allProviders.forEach((provider) => {
        var fromServerGroups = _.pluck(_.filter(application.serverGroups.data, {provider: provider}), 'account'),
            fromLoadBalancers = _.pluck(_.filter(application.loadBalancers.data, {type: provider}), 'account'),
            fromSecurityGroups = _.pluck(_.filter(application.securityGroups.data, {type: provider}), 'accountName');
        var allRegions = _.union(fromServerGroups, fromLoadBalancers, fromSecurityGroups);
        if (allRegions.length === 1) {
          application.defaultCredentials[provider] = allRegions[0];
        }
      });
    }

    function setApplicationAccounts(application) {
      var securityGroupAccounts = _(application.securityGroups.data).pluck('account').unique().value(),
          loadBalancerAccounts = _(application.loadBalancers.data).pluck('account').unique().value();

      application.accounts = _([application.accounts, securityGroupAccounts, loadBalancerAccounts])
        .flatten()
        .compact()
        .unique()
        .value();
    }

    function applicationLoadSuccess(application) {
      addDefaultRegion(application);
      addDefaultCredentials(application);
      setApplicationAccounts(application);
      application.lastRefresh = new Date().getTime();
      application.refreshStream.onNext();
      return application;
    }

    function applicationLoadError(err) {
      $log.error(err, 'Failed to load application, will retry on next scheduler execution.');
    }


    return {
      listApplications: listApplications,
      getApplication: getApplication,
      addSectionToApplication: addSectionToApplication, // exposed for testing
    };
  });
