'use strict';

describe('Controller: modifyScalingProcesses', function() {
  const angular = require('angular');

  beforeEach(
    window.module(
      require('./modifyScalingProcesses.controller.js')
    )
  );

  beforeEach(window.inject(function($controller, $rootScope, _) {
    this.$modalInstance = { close: angular.noop };
    this.taskMonitorService = { buildTaskMonitor: angular.noop };
    this.taskExecutor = { executeTask: angular.noop };
    this.$scope = $rootScope.$new();

    this.initializeController = function(serverGroup, processes) {
      this.processes = processes;

      this.controller = $controller('ModifyScalingProcessesCtrl', {
        $scope: this.$scope,
        serverGroup: serverGroup,
        processes: this.processes,
        application: {},
        taskMonitorService: this.taskMonitorService,
        taskExecutor: this.taskExecutor,
        $modalInstance: this.$modalInstance,
        _: _,
      });
    };
  }));

  describe('isDirty', function() {

    beforeEach(function() {
      this.serverGroup = {name: 'the-asg'};
      this.processes = [
        { name: 'Launch', enabled: true },
        { name: 'Terminate', enabled: true }
      ];
    });
    it('starts as not dirty', function() {
      this.initializeController(this.serverGroup, this.processes);
      expect(this.controller.isDirty()).toBe(false);
    });

    it('becomes dirty when a process is changed, becomes clean when the process is changed back', function() {
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

  describe('form submission', function() {
    beforeEach(function() {
      this.serverGroup = {name: 'the-asg', region: 'us-east-1', account: 'test'};
      this.processes = [
        { name: 'Launch', enabled: true },
        { name: 'Terminate', enabled: true },
        { name: 'AddToLoadBalancer', enabled: false }
      ];
      this.taskMonitor = { submit: angular.noop };
      spyOn(this.taskMonitorService, 'buildTaskMonitor').and.returnValue(this.taskMonitor);
      spyOn(this.taskMonitor, 'submit').and.callFake(function(method) { method(); });
      spyOn(this.taskExecutor, 'executeTask');
    });

    it('sends a resume job when processes are enabled', function() {
      this.initializeController(this.serverGroup, this.processes);
      this.$scope.command[2].enabled = true;
      this.controller.submit();
      var job = this.taskExecutor.executeTask.calls.mostRecent().args[0].job;

      expect(job.length).toBe(1);
      expect(job[0].action).toBe('resume');
      expect(job[0].processes).toEqual(['AddToLoadBalancer']);
    });

    it('sends a suspend job when processes are enabled', function() {
      this.initializeController(this.serverGroup, this.processes);
      this.$scope.command[1].enabled = false;
      this.controller.submit();
      var job = this.taskExecutor.executeTask.calls.mostRecent().args[0].job;

      expect(job.length).toBe(1);
      expect(job[0].action).toBe('suspend');
      expect(job[0].processes).toEqual(['Terminate']);
    });

    it('sends both a resume and suspend job when processes are enabled', function() {
      this.initializeController(this.serverGroup, this.processes);
      this.$scope.command[0].enabled = false;
      this.$scope.command[2].enabled = true;
      this.controller.submit();
      var job = this.taskExecutor.executeTask.calls.mostRecent().args[0].job;

      expect(job.length).toBe(2);
      expect(job[0].action).toBe('resume');
      expect(job[0].processes).toEqual(['AddToLoadBalancer']);
      expect(job[1].action).toBe('suspend');
      expect(job[1].processes).toEqual(['Launch']);
    });
  });

});
