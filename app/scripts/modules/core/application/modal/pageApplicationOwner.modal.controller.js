'use strict';

import {APPLICATION_WRITE_SERVICE} from 'core/application/service/application.write.service';

module.exports = angular.module('spinnaker.pageApplicationOwner.modal.controller', [
  APPLICATION_WRITE_SERVICE,
  require('core/task/monitor/taskMonitorService.js'),
])
  .controller('PageApplicationOwner', function ($scope, $uibModalInstance,
                                                taskMonitorService, applicationWriter, application) {
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

      this.taskMonitor = taskMonitorService.buildTaskMonitor(taskMonitorConfig);
      this.taskMonitor.submit(submitMethod);
    };
  });
