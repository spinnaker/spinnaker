'use strict';

import { Subject } from 'rxjs';
import { CloudMetricsReader } from '@spinnaker/core';

describe('Component: metricAlarmChart', function () {
  var $ctrl, $scope, $q;

  beforeEach(window.module(require('./metricAlarmChart.component').name));

  beforeEach(
    window.inject(function ($componentController, $rootScope, _$q_) {
      $scope = $rootScope.$new();
      $q = _$q_;

      this.initialize = (bindings) => {
        $ctrl = $componentController('metricAlarmChart', { $scope }, bindings);
      };
    }),
  );

  describe('initialization: default data', function () {
    it('sets defaults for margins, ticks, alarmUpdated if not provided', function () {
      let alarm = {
        comparisonOperator: 'GreaterThanThreshold',
        dimensions: [{ name: 'AutoScalingGroupName', value: 'asg-v000' }],
      };

      this.initialize({ alarm: alarm, serverGroup: {} });

      expect($ctrl.alarmUpdated).toBeUndefined();

      $ctrl.$onInit();

      expect($ctrl.chartOptions.margin).toEqual({ top: 5, left: 5 });
      expect($ctrl.chartOptions.axes).toEqual({
        x: { key: 'timestamp', type: 'date', ticks: 6 },
        y: { ticks: 3 },
        x2: { ticks: 0 },
        y2: { ticks: 0 },
      });
      expect($ctrl.alarmUpdated).not.toBeUndefined();
    });

    it('uses supplied margins, ticks, alarmUpdated', function () {
      let alarm = {
          comparisonOperator: 'GreaterThanThreshold',
          dimensions: [{ name: 'AutoScalingGroupName', value: 'asg-v000' }],
        },
        alarmUpdated = new Subject(),
        ticks = { x: 3, y: 4, x2: 1, y2: 1 }, // x2, y2 ignored
        margins = { top: 3, left: 4, theseAreCopiedOverWholesale: 5 };

      this.initialize({ alarm: alarm, serverGroup: {}, alarmUpdated: alarmUpdated, ticks: ticks, margins: margins });

      $ctrl.$onInit();

      expect($ctrl.chartOptions.margin).toBe(margins);
      expect($ctrl.chartOptions.axes).toEqual({
        x: { key: 'timestamp', type: 'date', ticks: 3 },
        y: { ticks: 4 },
        x2: { ticks: 0 },
        y2: { ticks: 0 },
      });
      expect($ctrl.alarmUpdated).toBe(alarmUpdated);
    });
  });

  describe('data retrieval', function () {
    var alarm, serverGroup;

    beforeEach(function () {
      alarm = {
        comparisonOperator: 'GreaterThanThreshold',
        dimensions: [{ name: 'AutoScalingGroupName', value: 'asg-v000' }],
        metricName: 'CPUUtilization',
        namespace: 'aws/ec2',
        period: 300,
      };
      serverGroup = {
        type: 'aws',
        account: 'test',
        region: 'us-east-1',
      };
    });

    it('sets loading flag, fetches data, then converts datapoints and applies them to chartData', function () {
      spyOn(CloudMetricsReader, 'getMetricStatistics').and.returnValue(
        $q.when({
          datapoints: [{ timestamp: 1 }, { timestamp: 2 }],
        }),
      );

      this.initialize({ alarm: alarm, serverGroup: serverGroup });

      $ctrl.$onInit();

      expect($ctrl.chartData.loading).toBe(true);

      $scope.$digest();

      expect($ctrl.chartData.datapoints.map((p) => p.timestamp.getTime())).toEqual([1, 2]);

      expect($ctrl.chartData.loading).toBe(false);
      expect($ctrl.chartData.noData).toBe(false);
    });

    it('sets noData flag when datapoints is missing from response', function () {
      spyOn(CloudMetricsReader, 'getMetricStatistics').and.returnValue($q.when({}));
      this.initialize({ alarm: alarm, serverGroup: serverGroup });
      $ctrl.$onInit();
      $scope.$digest();
      expect($ctrl.chartData.loading).toBe(false);
      expect($ctrl.chartData.noData).toBe(true);
    });

    it('sets noData flag when datapoints is empty in response', function () {
      spyOn(CloudMetricsReader, 'getMetricStatistics').and.returnValue($q.when({ datapoints: [] }));
      this.initialize({ alarm: alarm, serverGroup: serverGroup });
      $ctrl.$onInit();
      $scope.$digest();
      expect($ctrl.chartData.loading).toBe(false);
      expect($ctrl.chartData.noData).toBe(true);
    });

    it('sets noData flag when request fails', function () {
      spyOn(CloudMetricsReader, 'getMetricStatistics').and.returnValue($q.reject({ datapoints: [{ timestamp: 1 }] }));
      this.initialize({ alarm: alarm, serverGroup: serverGroup });
      $ctrl.$onInit();
      $scope.$digest();
      expect($ctrl.chartData.loading).toBe(false);
      expect($ctrl.chartData.noData).toBe(true);
      expect($ctrl.chartData.datapoints).toEqual([]);
    });
  });

  describe('chart line configuration', function () {
    var alarm;

    beforeEach(function () {
      alarm = {
        comparisonOperator: 'GreaterThanThreshold',
        dimensions: [{ name: 'AutoScalingGroupName', value: 'asg-v000' }],
        metricName: 'CPUUtilization',
        namespace: 'aws/ec2',
        period: 300,
      };
    });

    it('sets a baseline at zero', function () {
      this.initialize({ alarm: alarm, serverGroup: {} });
      $ctrl.$onInit();
      expect($ctrl.chartData.baseline.map((d) => d.val)).toEqual([0, 0]);
    });

    it('sets a threshold at zero if not defined on alarm', function () {
      this.initialize({ alarm: alarm, serverGroup: {} });
      $ctrl.$onInit();
      expect($ctrl.chartData.threshold.map((d) => d.val)).toEqual([0, 0]);
    });

    it('uses alarm threshold if available', function () {
      alarm.threshold = 3.1;
      this.initialize({ alarm: alarm, serverGroup: {} });
      $ctrl.$onInit();
      expect($ctrl.chartData.threshold.map((d) => d.val)).toEqual([3.1, 3.1]);
    });

    it('sets topline to 1.02x threshold when alarm comparator is >=', function () {
      alarm.threshold = 10;
      alarm.comparisonOperator = 'GreaterThanOrEqualToThreshold';
      this.initialize({ alarm: alarm, serverGroup: {} });
      $ctrl.$onInit();
      expect($ctrl.chartData.topline.map((d) => d.val)).toEqual([10.2, 10.2]);
    });

    it('sets topline to 1.02x threshold when alarm comparator is >', function () {
      alarm.threshold = 100;
      alarm.comparisonOperator = 'GreaterThanThreshold';
      this.initialize({ alarm: alarm, serverGroup: {} });
      $ctrl.$onInit();
      expect($ctrl.chartData.topline.map((d) => d.val)).toEqual([102, 102]);
    });

    it('sets topline to 3 * threshold when alarm comparator is <', function () {
      alarm.threshold = 3.1;
      alarm.comparisonOperator = 'LessThanThreshold';
      this.initialize({ alarm: alarm, serverGroup: {} });
      $ctrl.$onInit();
      expect($ctrl.chartData.topline.map((d) => d.val)).toEqual([9.3, 9.3]);
    });

    it('sets topline to 3 * threshold when alarm comparator is <=', function () {
      alarm.threshold = 3.1;
      alarm.comparisonOperator = 'LessThanOrEqualToThreshold';
      this.initialize({ alarm: alarm, serverGroup: {} });
      $ctrl.$onInit();
      expect($ctrl.chartData.topline.map((d) => d.val)).toEqual([9.3, 9.3]);
    });
  });

  describe('chart refreshing', function () {
    var updater;
    var alarm;

    beforeEach(function () {
      updater = new Subject();
      alarm = {
        comparisonOperator: 'LessThanThreshold',
        threshold: 5,
        dimensions: [{ name: 'AutoScalingGroupName', value: 'asg-v000' }],
        metricName: 'CPUUtilization',
        namespace: 'aws/ec2',
        period: 300,
      };
    });

    it('updates chart and data when updater triggers', function () {
      spyOn(CloudMetricsReader, 'getMetricStatistics').and.returnValue($q.when({}));
      this.initialize({ alarm: alarm, serverGroup: {}, alarmUpdated: updater });
      $ctrl.$onInit();
      $scope.$digest();

      expect(CloudMetricsReader.getMetricStatistics.calls.count()).toBe(1);
      expect($ctrl.chartData.threshold.map((d) => d.val)).toEqual([5, 5]);

      alarm.threshold = 6;
      updater.next();
      expect(CloudMetricsReader.getMetricStatistics.calls.count()).toBe(2);
      expect($ctrl.chartData.threshold.map((d) => d.val)).toEqual([6, 6]);
    });
  });
});
