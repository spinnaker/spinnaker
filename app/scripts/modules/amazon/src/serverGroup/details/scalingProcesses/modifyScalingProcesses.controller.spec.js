'use strict';

import * as angular from 'angular';
import { TaskExecutor } from '@spinnaker/core';

describe('Controller: modifyScalingProcesses', function () {
  beforeEach(window.module(require('./modifyScalingProcesses.controller').name));

  beforeEach(
    window.inject(function ($controller, $rootScope, $q) {
      this.$uibModalInstance = { close: angular.noop, result: { then: angular.noop } };
      this.$scope = $rootScope.$new();

      this.initializeController = function (serverGroup, processes) {
        this.processes = processes;
        spyOn(TaskExecutor, 'executeTask').and.returnValue($q.when(null));

        this.controller = $controller('ModifyScalingProcessesCtrl', {
          $scope: this.$scope,
          serverGroup: serverGroup,
          processes: this.processes,
          application: { serverGroups: { refresh: angular.noop } },
          $uibModalInstance: this.$uibModalInstance,
        });
      };
    }),
  );

  describe('isDirty', function () {
    beforeEach(function () {
      this.serverGroup = { name: 'the-asg' };
      this.processes = [
        { name: 'Launch', enabled: true },
        { name: 'Terminate', enabled: true },
      ];
    });
    it('starts as not dirty', function () {
      this.initializeController(this.serverGroup, this.processes);
      expect(this.controller.isDirty()).toBe(false);
    });

    it('becomes dirty when a process is changed, becomes clean when the process is changed back', function () {
      this.initializeController(this.serverGroup, this.processes);
      expect(this.controller.isDirty()).toBe(false);

      this.$scope.command[0].enabled = false;
      expect(this.controller.isDirty()).toBe(true);

      this.$scope.command[1].enabled = false;
      expect(this.controller.isDirty()).toBe(true);

      this.$scope.command[0].enabled = true;
      expect(this.controller.isDirty()).toBe(true);

      this.$scope.command[1].enabled = true;
      expect(this.controller.isDirty()).toBe(false);
    });
  });

  describe('form submission', function () {
    beforeEach(function () {
      this.serverGroup = { name: 'the-asg', region: 'us-east-1', account: 'test' };
      this.processes = [
        { name: 'Launch', enabled: true },
        { name: 'Terminate', enabled: true },
        { name: 'AddToLoadBalancer', enabled: false },
      ];
    });

    it('sends a resume job when processes are enabled', function () {
      this.initializeController(this.serverGroup, this.processes);
      this.$scope.command[2].enabled = true;
      this.controller.submit();
      var job = TaskExecutor.executeTask.calls.mostRecent().args[0].job;

      expect(job.length).toBe(1);
      expect(job[0].action).toBe('resume');
      expect(job[0].processes).toEqual(['AddToLoadBalancer']);
    });

    it('sends a suspend job when processes are enabled', function () {
      this.initializeController(this.serverGroup, this.processes);
      this.$scope.command[1].enabled = false;
      this.controller.submit();
      var job = TaskExecutor.executeTask.calls.mostRecent().args[0].job;

      expect(job.length).toBe(1);
      expect(job[0].action).toBe('suspend');
      expect(job[0].processes).toEqual(['Terminate']);
    });

    it('sends both a resume and suspend job when processes are enabled', function () {
      this.initializeController(this.serverGroup, this.processes);
      this.$scope.command[0].enabled = false;
      this.$scope.command[2].enabled = true;
      this.controller.submit();
      var job = TaskExecutor.executeTask.calls.mostRecent().args[0].job;

      expect(job.length).toBe(2);
      expect(job[0].action).toBe('resume');
      expect(job[0].processes).toEqual(['AddToLoadBalancer']);
      expect(job[1].action).toBe('suspend');
      expect(job[1].processes).toEqual(['Launch']);
    });
  });
});
