'use strict';

import {APPLICATION_WRITE_SERVICE} from 'core/application/service/application.write.service';
import {TASK_MONITOR_BUILDER} from 'core/task/monitor/taskMonitor.builder';

module.exports = angular.module('spinnaker.pageApplicationOwner.modal.controller', [
  APPLICATION_WRITE_SERVICE,
  TASK_MONITOR_BUILDER,
])
  .controller('PageApplicationOwner', function ($scope, $uibModalInstance,
                                                taskMonitorBuilder, applicationWriter, application) {
    this.application = application;
    this.command = {};

    this.submit = () => {
      var taskMonitorConfig = {
        application: application,
        title: 'Paging ' + application.name + ' owner',
        modalInstance: $uibModalInstance
      };

      var submitMethod = () => {
        var reason = '[' + this.application.name.toUpperCase() + '] ' + this.command.reason;
        return applicationWriter.pageApplicationOwner(
          this.application, reason
        );
      };

      this.taskMonitor = taskMonitorBuilder.buildTaskMonitor(taskMonitorConfig);
      this.taskMonitor.submit(submitMethod);
    };
  });
