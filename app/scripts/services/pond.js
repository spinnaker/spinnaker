'use strict';

angular.module('deckApp')
  .factory('pond', function(settings, Restangular, dateFromTimestampFilter, listOfTasks) {

    var filterTimes = function(elem, isLast) {
      elem.startTimeAsDate = dateFromTimestampFilter(elem.startTime);
      elem.endTimeAsDate = dateFromTimestampFilter(elem.endTime);
      // map elem status to bootstrap labels and create a diplay name
      // TODO: clean up
      elem.category = (elem.status == 'STARTED' ? 'running' :
        (elem.status != 'COMPLETED' ? 'danger' :
          (isLast ? 'success' : 'non-terminal')));
      elem.active = (elem.status == 'STARTED');
      elem.displayName = elem.name.split(/([A-Z][a-z]*)/)
        .filter(function(e) { return e.length > 0 && e != 'Step'; })
        .join(' ');
      return elem;
    };

    var filterTask = function(task) {
      var elapsedTime = task.status != 'STARTED' ? task.endTime - task.startTime : Date.now() - task.startTime;
      filterTimes(task, true);
      task.steps.forEach(function(step, idx) {
        filterTimes(step, (task.steps.length - 1 === idx));
        step.percentage = (step.status != 'STARTED'  ? step.endTime - step.startTime : Date.now() - step.startTime) * 100 / elapsedTime;
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

      RestangularConfigurer.addResponseInterceptor(function() {
        return listOfTasks;
      });

    });
  });
