import { mock, IComponentControllerService, IQService, IScope, IRootScopeService } from 'angular';

import { Subject } from 'rxjs';

import { CloudMetricsReader, IMetricAlarmDimension, IServerGroup } from '@spinnaker/core';

import { AlarmComparisonOperator } from '../../../../../domain';
import { IUpsertAlarmDescription } from '../../../..';
import { METRIC_SELECTOR_COMPONENT, MetricSelectorController } from './metricSelector.component';

describe('Component: metric selector', () => {
  let $ctrl: MetricSelectorController;
  let $componentController: IComponentControllerService;
  let $q: IQService;
  let $scope: IScope;
  const alarmUpdated = new Subject<void>();

  let alarm: IUpsertAlarmDescription;
  let serverGroup: IServerGroup;

  beforeEach(mock.module(METRIC_SELECTOR_COMPONENT));

  beforeEach(
    mock.inject(
      (_$componentController_: IComponentControllerService, _$q_: IQService, $rootScope: IRootScopeService) => {
        $componentController = _$componentController_;
        $q = _$q_;
        $scope = $rootScope.$new();
      },
    ),
  );

  const initialize = () => {
    $ctrl = $componentController(
      'awsMetricSelector',
      { $scope },
      { alarm, serverGroup, alarmUpdated },
    ) as MetricSelectorController;
    $ctrl.$onInit();
  };

  const makeServerGroup = (name: string): IServerGroup => {
    return {
      name,
      account: 'test',
      cloudProvider: 'aws',
      cluster: undefined,
      region: 'us-east-1',
      type: undefined,
      instanceCounts: undefined,
      instances: undefined,
    };
  };

  const makeAlarm = (
    namespace: string,
    metricName: string,
    comparisonOperator: AlarmComparisonOperator,
    dimensions: IMetricAlarmDimension[],
  ): IUpsertAlarmDescription => {
    return {
      asgName: undefined,
      name: undefined,
      region: undefined,
      alarmDescription: undefined,
      evaluationPeriods: undefined,
      period: undefined,
      threshold: undefined,
      statistic: undefined,
      unit: undefined,
      alarmActionArns: undefined,
      insufficientDataActionArns: undefined,
      okActionArns: undefined,
      comparisonOperator,
      dimensions,
      metricName,
      namespace,
    };
  };

  describe('initialization', () => {
    beforeEach(() => {
      alarm = makeAlarm('AWS/EC2', 'CPUUtilization', 'GreaterThanThreshold', [
        {
          name: 'AutoScalingGroupName',
          value: 'asg-v000',
        },
      ]);
    });

    it('sets advanced mode when dimensions are non-standard', () => {
      serverGroup = makeServerGroup('asg-v000');
      initialize();
      expect($ctrl.state.advancedMode).toBe(false);

      serverGroup = makeServerGroup('other-v001');
      initialize();
      $ctrl.$onInit();
      expect($ctrl.state.advancedMode).toBe(true);
    });

    it('updates available metrics on initialization, triggers alarmUpdated once', () => {
      spyOn(CloudMetricsReader, 'listMetrics').and.returnValue(
        $q.when([
          {
            namespace: 'AWS/EC2',
            name: 'CPUUtilization',
            dimensions: [{ name: 'AutoScalingGroupName', value: 'asg-v000' }],
          },
          {
            namespace: 'AWS/EC2',
            name: 'NetworkIn',
            dimensions: [{ name: 'AutoScalingGroupName', value: 'asg-v000' }],
          },
        ]),
      );
      serverGroup = makeServerGroup('asg-v000');
      initialize();
      const alarmUpdatedSpy = spyOn($ctrl.alarmUpdated, 'next');
      $ctrl.$onInit();
      $scope.$digest();

      expect($ctrl.state.metrics.length).toBe(2);
      expect($ctrl.state.metrics.map((m) => m.name)).toEqual(['CPUUtilization', 'NetworkIn']);
      expect($ctrl.state.selectedMetric).toBe($ctrl.state.metrics[0]);
      expect($ctrl.state.metricsLoaded).toBe(true);
      expect(alarmUpdatedSpy.calls.count()).toBe(1);
    });
  });

  describe('metricChanged', () => {
    beforeEach(() => {
      alarm = makeAlarm('AWS/EC2', 'CPUUtilization', 'GreaterThanThreshold', [
        { name: 'AutoScalingGroupName', value: 'asg-v000' },
      ]);
      serverGroup = makeServerGroup('asg-v000');
      spyOn(CloudMetricsReader, 'listMetrics').and.returnValue(
        $q.when([
          {
            namespace: 'AWS/EC2',
            name: 'CPUUtilization',
            dimensions: [{ name: 'AutoScalingGroupName', value: 'asg-v000' }],
          },
          {
            namespace: 'AWS/EC2',
            name: 'NetworkIn',
            dimensions: [
              { name: 'AutoScalingGroupName', value: 'asg-v000' },
              { name: 'sr', value: '71' },
            ],
          },
        ]),
      );
      initialize();
      $scope.$digest();
    });

    it('triggers alarmUpdated, updates alarm fields when new metric selected', () => {
      const alarmSpy = spyOn($ctrl.alarmUpdated, 'next');

      $ctrl.state.selectedMetric = $ctrl.state.metrics[1];
      $ctrl.metricChanged();
      expect(alarm.metricName).toBe('NetworkIn');
      expect(alarmSpy.calls.count()).toBe(1);
    });

    it('updates dimensions, available metrics when dimensions on selected metric are different', () => {
      const updateSpy = spyOn($ctrl, 'updateAvailableMetrics');

      $ctrl.state.selectedMetric = $ctrl.state.metrics[1];
      $ctrl.metricChanged();

      expect(alarm.dimensions.length).toBe(2);
      expect(updateSpy.calls.count()).toBe(1);
    });

    it('clears namespace when not in advanced mode and selected metric is removed', () => {
      const alarmSpy = spyOn($ctrl.alarmUpdated, 'next');
      $ctrl.state.selectedMetric = null;
      $ctrl.metricChanged();
      expect(alarm.namespace).toBe(null);
      expect(alarmSpy.calls.count()).toBe(1);
    });

    it('does not clear namespace when in advanced mode and selected metric is removed', () => {
      const alarmSpy = spyOn($ctrl.alarmUpdated, 'next');
      $ctrl.state.advancedMode = true;
      $ctrl.state.selectedMetric = null;
      $ctrl.metricChanged();
      expect(alarm.namespace).toBe('AWS/EC2');
      expect(alarmSpy.calls.count()).toBe(1);
    });
  });

  describe('metric transformations', () => {
    beforeEach(() => {
      alarm = makeAlarm('AWS/EC2', 'CPUUtilization', 'GreaterThanThreshold', [
        { name: 'AutoScalingGroupName', value: 'asg-v000' },
      ]);
      serverGroup = makeServerGroup('asg-v000');
      spyOn(CloudMetricsReader, 'listMetrics').and.returnValue(
        $q.when([
          {
            namespace: 'AWS/EC2',
            name: 'CPUUtilization',
            dimensions: [{ name: 'AutoScalingGroupName', value: 'asg-v000' }],
          },
          {
            namespace: 'AWS/EC2',
            name: 'NetworkIn',
            dimensions: [
              { name: 'AutoScalingGroupName', value: 'asg-v000' },
              { name: 'sr', value: '71' },
            ],
          },
          {
            namespace: 'AWS/EBS',
            name: 'somethingElse',
            dimensions: [],
          },
        ]),
      );
      initialize();
      $scope.$digest();
    });

    it('adds label to each metric, sorts by label', () => {
      expect($ctrl.state.metrics.map((m) => m.label)).toEqual([
        '(AWS/EBS) somethingElse',
        '(AWS/EC2) CPUUtilization',
        '(AWS/EC2) NetworkIn',
      ]);
    });

    it('adds default dimensions if not present', () => {
      expect($ctrl.state.metrics[0].dimensions).toEqual([]);
    });

    it('adds dimensionValues to each dimension', () => {
      expect($ctrl.state.metrics.map((m) => m.dimensionValues)).toEqual(['', 'asg-v000', 'asg-v000, 71']);
    });
  });

  describe('update available metrics', () => {
    it('sets advanced mode when metrics fail to load', () => {
      alarm = makeAlarm('AWS/EC2', 'CPUUtilization', 'GreaterThanThreshold', [
        {
          name: 'AutoScalingGroupName',
          value: 'asg-v000',
        },
      ]);
      serverGroup = makeServerGroup('asg-v000');

      spyOn(CloudMetricsReader, 'listMetrics').and.returnValue($q.reject(null));
      initialize();

      $ctrl.$onInit();
      $scope.$digest();

      expect($ctrl.state.metricsLoaded).toBe(true);
      expect($ctrl.state.advancedMode).toBe(true);
    });
  });
});
