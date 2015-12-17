'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.delivery.filter.executionFilter.service', [
    require('angular-ui-router'),
    require('exports?"debounce"!angular-debounce'),
    require('./executionFilter.model.js'),
    require('../../utils/lodash.js'),
    require('../../utils/waypoints/waypoint.service.js'),
    require('../../filterModel/filter.model.service.js'),
    require('../../orchestratedItem/timeBoundaries.service.js'),
  ])
  .factory('executionFilterService', function (ExecutionFilterModel, _, timeBoundaries, waypointService, $log,
                                               filterModelService, debounce) {

    var lastApplication = null;

    /**
     * Filtering logic
     */

    var isFilterable = filterModelService.isFilterable;

    function pipelineNameFilter(execution) {
      if(isFilterable(ExecutionFilterModel.sortFilter.pipeline)) {
        var checkedPipelineNames = filterModelService.getCheckValues(ExecutionFilterModel.sortFilter.pipeline);
        return _.contains(checkedPipelineNames, execution.name);
      } else {
        return true;
      }
    }

    function getValuesAsString(object, blacklist=[]) {
      if (typeof object === 'string' || typeof object === 'number') {
        return object;
      }
      if (object instanceof Array) {
        return object.map((val) => getValuesAsString(val, blacklist)).join(' ');
      }
      if (object instanceof Object) {
        return Object.keys(object).map((key) => {
          if (blacklist.indexOf(key) !== -1) {
            return '';
          }
          return getValuesAsString(object[key], blacklist);
        }).join(' ');
      }
      return '';
    }

    function addSearchText(execution) {
      if (execution.searchField) {
        return;
      }
      let searchText = [execution.name];
      searchText.push(execution.id);
      searchText.push(getValuesAsString(execution.appConfig));
      searchText.push(getValuesAsString(execution.trigger));
      execution.stages.forEach((stage) => searchText.push(getValuesAsString(stage.context, ['commits', 'jarDiffs', 'kato.tasks'])));
      execution.searchField = searchText.join(' ').toLowerCase();
    }

    function textFilter(execution) {
      addSearchText(execution);
      var filter = ExecutionFilterModel.sortFilter.filter.toLowerCase();
      if (!filter) {
        return true;
      }
      return execution.searchField.indexOf(filter) !== -1;
    }

    function statusFilter(execution) {
      if (isFilterable(ExecutionFilterModel.sortFilter.status)) {
        var checkedStatus = filterModelService.getCheckValues(ExecutionFilterModel.sortFilter.status);
        return _.contains(checkedStatus, execution.status);
      } else {
        return true;
      }
    }

    function filterExecutionsForDisplay(executions) {
      return  _.chain(executions)
        .filter(textFilter)
        .filter(pipelineNameFilter)
        .filter(statusFilter)
        .value();
    }

    function addEmptyPipelines(groups, application) {
      let configs = application.pipelineConfigs || [];
      if (!isFilterable(ExecutionFilterModel.sortFilter.pipeline) &&
        !isFilterable(ExecutionFilterModel.sortFilter.status) &&
        !ExecutionFilterModel.sortFilter.filter) {
        configs
          .filter((config) => !groups[config.name])
          .forEach((config) => groups.push({heading: config.name, config: config, executions: []}));
      } else {
        configs
          .filter((config) => !groups[config.name] && ExecutionFilterModel.sortFilter.pipeline[config.name])
          .forEach((config) => groups.push({heading: config.name, config: config, executions: []}));
      }
    }

    function fixName(execution, application) {
      let config = _.find(application.pipelineConfigs, { id: execution.pipelineConfigId});
      if (config) {
        execution.name = config.name;
      }
    }

    /**
     * Grouping logic
     */

    function groupExecutions(executions, application) {
      var groups = [];
      if (ExecutionFilterModel.sortFilter.groupBy === 'name') {
        var executionGroups = _.groupBy(executions, 'name');
        _.forOwn(executionGroups, function (executions, key) {
          let config = application.pipelineConfigs.filter((config) => config.id === executions[0].pipelineConfigId);
          executions.sort(executionSorter);
          groups.push({
            heading: key,
            config: config ? config[0] : null,
            executions: executions.slice(0, ExecutionFilterModel.sortFilter.count),
            runningExecutions: executions.filter((execution) => execution.isActive),
          });
        });
        addEmptyPipelines(groups, application);
      }

      if (ExecutionFilterModel.sortFilter.groupBy === 'timeBoundary') {
        var grouped = timeBoundaries.groupByTimeBoundary(executions);
        _.forOwn(grouped, function (executions, key) {
          executions.sort(executionSorter);
          groups.push({
            heading: key,
            config: null,
            executions: executions.slice(0, ExecutionFilterModel.sortFilter.count),
            runningExecutions: executions.filter((execution) => execution.isActive),
          });
        });
      }

      if (ExecutionFilterModel.sortFilter.groupBy === 'none') {
        executions.sort(executionSorter);
        groups.push({
          heading: '',
          executions: executions,
          runningExecutions: [],
        });
      }

      return groups;
    }

    // this gets called every time the URL changes, so we debounce it a tiny bit
    var updateExecutionGroups = debounce((application) => {
      if (!application) {
        application = lastApplication;
        if (!lastApplication) {
          return null;
        }
      }
      let executions = application.executions || [];
      executions.forEach((execution) => fixName(execution, application));
      var filtered = filterExecutionsForDisplay(application.executions);

      var groups = groupExecutions(filtered, application);

      applyGroupsToModel(groups);
      waypointService.restoreToWaypoint(application.name);
      ExecutionFilterModel.addTags();
      lastApplication = application;
      return groups;
    }, 25);

    function diffExecutionGroups(oldGroups, newGroups) {
      var groupsToRemove = [];

      oldGroups.forEach(function(oldGroup, idx) {
        var newGroup = _.find(newGroups, { heading: oldGroup.heading });
        if (!newGroup) {
          groupsToRemove.push(idx);
        } else {
          oldGroup.runningExecutions = newGroup.runningExecutions;
          diffExecutions(oldGroup, newGroup);
        }
      });
      groupsToRemove.reverse().forEach(function(idx) {
        oldGroups.splice(idx, 1);
      });
      newGroups.forEach(function(newGroup) {
        var match = _.find(oldGroups, { heading: newGroup.heading });
        if (!match) {
          oldGroups.push(newGroup);
        }
      });
      oldGroups.forEach((group) => group.executions.sort(executionSorter));
    }

    function diffExecutions(oldGroup, newGroup) {
      var toRemove = [];
      oldGroup.executions.forEach(function(execution, idx) {
        var newExecution = _.find(newGroup.executions, { id: execution.id });
        if (!newExecution) {
          $log.debug('execution no longer found, removing:', execution.id);
          toRemove.push(idx);
        } else {
          if (execution.stringVal !== newExecution.stringVal) {
            $log.debug('change detected, updating execution:', execution.id);
            oldGroup.executions[idx] = newExecution;
          }
        }
      });
      toRemove.reverse().forEach(function(idx) {
        oldGroup.executions.splice(idx, 1);
      });
      newGroup.executions.forEach(function(execution) {
        var oldExecution = _.find(oldGroup.executions, { id: execution.id });
        if (!oldExecution) {
          $log.debug('new execution found, adding', execution.id);
          oldGroup.executions.push(execution);
        }
      });
    }

    function applyGroupsToModel(groups) {
      diffExecutionGroups(ExecutionFilterModel.groups, groups);

      // sort groups in place so Angular doesn't try to update the world
      ExecutionFilterModel.groups.sort(executionGroupSorter);
    }

    function executionGroupSorter(a, b) {
      if (ExecutionFilterModel.sortFilter.groupBy === 'timeBoundary') {
        return b.executions[0].startTime - a.executions[0].startTime;
      }

      if (a.config && b.config) {
        return a.config.index - b.config.index;
      }
      if (a.config) {
        return -1;
      }
      if (b.config) {
        return 1;
      }
      if (a.heading < b.heading) {
        return -1;
      }
      if (a.heading > b.heading) {
        return 1;
      }
      return 0;
    }

    function executionSorter(a, b) {
      if (a.isActive && b.isActive) {
        return b.startTime - a.startTime;
      }
      if (a.isActive) {
        return -1;
      }
      if (b.isActive) {
        return 1;
      }
      return b.endTime - a.endTime;
    }

    function clearFilters() {
      ExecutionFilterModel.clearFilters();
      ExecutionFilterModel.applyParamsToUrl();
    }

    return {
      updateExecutionGroups: updateExecutionGroups,
      filterExecutionsForDisplay: filterExecutionsForDisplay,
      sortGroupsByHeading: applyGroupsToModel,
      clearFilters: clearFilters,
    };
  }
);

