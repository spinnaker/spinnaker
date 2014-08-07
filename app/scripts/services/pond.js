'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('pond', function(settings, Restangular, dateFromTimestampFilter, momentService) {

    var filterTimes = function(elem, isLast) {
      elem.startTimeAsDate = dateFromTimestampFilter(elem.startTime);
      elem.endTimeAsDate = dateFromTimestampFilter(elem.endTime);
      elem.runningTime = momentService.duration(elem.endTime - elem.startTime).humanize();
      // map elem status to bootstrap labels and create a diplay name
      // TODO: clean up
      elem.category = (elem.status === 'STARTED' ? 'running' :
        (elem.status !== 'COMPLETED' ? 'errored' :
          (isLast ? 'success' : 'non-terminal')));
      elem.active = (elem.status === 'STARTED');
      return elem;
    };

    var filterTask = function(task) {
      filterTimes(task, true);
      // TODO: this is temporary to filter out orca steps -- removed when unneeded
      task.stages = task.steps.reduce(function(acc, current) {
        current.displayName = current.name.split(/([A-Z][a-z]*)/)
          .filter(function(e) { return e.length > 0 && e !== 'Step' && e !== 'Create' && e !== 'Monitor'; })
          .join(' ');
        var prev = acc[acc.length - 1];
        if (current.displayName.indexOf('orca') !== -1) {
          return acc;
        }
        if (angular.isDefined(prev) && current.displayName.indexOf(prev.displayName) !== -1) {
          prev.endTime = current.endTime;
          return acc;
        }
        acc.push(current);
        return acc;
      }, []);
      // end garbage
      // build the task duration from the individual steps.
      if (angular.isUndefined(task.stages[task.stages.length -1 ].endTime)) {
        task.stages[task.stages.length -1].endTime = Date.now();
      }
      var elapsedTime = task.stages.reduce(function(acc, step) {
        var endTime = Math.floor(step.endTime/10);
        var startTime = Math.floor(step.startTime/10);
        return acc + (endTime - startTime);
      }, 0);
      task.stages.forEach(function(step, idx) {
        filterTimes(step, (task.stages.length - 1 === idx));
        var endTime = Math.floor(step.endTime/10);
        var startTime = Math.floor(step.startTime/10);
        step.percentage = (endTime - startTime) / elapsedTime;
        /*
        step.percentage = (step.status != 'STARTED'  ? step.endTime - step.startTime : Date.now() - step.startTime) * 100 / elapsedTime;
        */
      });

      task.steps.forEach(function(step) {
        // TODO: remove when steps are rolled up by pond
        filterTimes(step);

      });
      return task;
    };

    return Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.pondUrl);

      RestangularConfigurer.addElementTransformer('task', false, function(task) {
        return filterTask(task);
      });

      RestangularConfigurer.addElementTransformer('task', true, function(taskCollection) {
        taskCollection.forEach(filterTask);
        return taskCollection;
      });

    });
  });
