'use strict';

describe('Service: autoScalingProcess ', function () {

  var service;

  beforeEach(
    window.module(
      require('./autoScalingProcess.service')
    )
  );

  beforeEach(window.inject(function(autoScalingProcessService) {
    service = autoScalingProcessService;
  }));

  describe('normalizeScalingProcesses', function() {

    it ('returns an empty list if no asg or suspendedProcesses present on server group', function () {
      expect(service.normalizeScalingProcesses({})).toEqual([]);
      expect(service.normalizeScalingProcesses({ asg: {} })).toEqual([]);
    });

    it ('returns all processes normalized if suspendedProcesses is empty', function () {
      let asg = {
        suspendedProcesses: []
      };
      let normalized = service.normalizeScalingProcesses({ asg: asg });
      expect(normalized.length).toBe(8);
      expect(normalized.filter((process) => process.enabled).length).toBe(8);
      expect(normalized.map((process) => process.name)).toEqual(
        ['Launch', 'Terminate', 'AddToLoadBalancer', 'AlarmNotification', 'AZRebalance', 'HealthCheck', 'ReplaceUnhealthy', 'ScheduledActions']
      );
      expect(normalized.filter((process) => process.suspensionDate)).toEqual([]);
    });

    it ('builds suspension date for suspended processes', function () {
      let asg = {
        suspendedProcesses: [
          {
            processName: 'Launch',
            suspensionReason: 'User suspended at 2016-01-12T22:59:46Z'
          }
        ]
      };
      let normalized = service.normalizeScalingProcesses({ asg: asg });
      expect(normalized.length).toBe(8);
      expect(normalized.filter((process) => process.enabled).length).toBe(7);
      expect(normalized.map((process) => process.name)).toEqual(
        ['Launch', 'Terminate', 'AddToLoadBalancer', 'AlarmNotification', 'AZRebalance', 'HealthCheck', 'ReplaceUnhealthy', 'ScheduledActions']
      );
      expect(normalized.filter((process) => process.suspensionDate).length).toBe(1);
      expect(normalized.filter((p) => p.suspensionDate).map((p) => p.suspensionDate)).toEqual([1452639586000]);
    });
  });

  describe('getDisabledDate', function() {
    it ('returns null when server group is not disabled, regardless of suspended processes', function () {
      let asg = {
        suspendedProcesses: [
          {
            processName: 'AddToLoadBalancer',
            suspensionReason: 'User suspended at 2016-01-12T22:59:46Z'
          }
        ]
      };
      expect(service.getDisabledDate({asg:asg})).toBeNull();
    });

    it ('returns null when server group is disabled but suspended process for AddToLoadBalancer not present', function () {
      let asg = {
        suspendedProcesses: []
      };
      expect(service.getDisabledDate({isDisabled: true, asg:asg})).toBeNull();
    });

    it ('returns suspension date when server group is disabled and AddToLoadBalancer suspended', function () {
      let asg = {
        suspendedProcesses: [
          {
            processName: 'AddToLoadBalancer',
            suspensionReason: 'User suspended at 2016-01-12T22:59:46Z'
          }
        ]
      };
      expect(service.getDisabledDate({isDisabled: true, asg:asg})).toEqual(1452639586000);
    });
  });

});
