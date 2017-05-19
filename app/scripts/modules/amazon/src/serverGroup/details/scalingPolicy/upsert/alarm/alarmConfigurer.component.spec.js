'use strict';

describe('Component: alarmConfigurer', function () {

  var $ctrl, $scope, cloudMetricsReader, $q, alarm;

  beforeEach(
    window.module(
      require('./alarmConfigurer.component')
    )
  );

  beforeEach(
    window.inject( function($componentController, $rootScope, _cloudMetricsReader_, _$q_) {
      $scope = $rootScope.$new();
      cloudMetricsReader = _cloudMetricsReader_;
      $q = _$q_;

      this.initialize = (bindings) => {
        $ctrl = $componentController('awsAlarmConfigurer', {
          $scope: $scope,
          cloudMetricsReader: cloudMetricsReader
        }, bindings);
      };
    })
  );


  describe('initialization', function () {
    beforeEach(function () {
      alarm = {
        comparisonOperator: 'GreaterThanThreshold',
        dimensions: [ { name: 'AutoScalingGroupName', value: 'asg-v000' }],
        metricName: 'CPUUtilization',
        namespace: 'AWS/EC2',
      };
    });

    it('sets advanced mode when dimensions are non-standard', function () {
      this.initialize({ command: {alarm: alarm}, serverGroup: { name: 'asg-v000'}, modalViewState: {} });
      $ctrl.$onInit();
      expect($ctrl.viewState.advancedMode).toBe(false);

      this.initialize({ command: {alarm: alarm}, serverGroup: { name: 'other-v001'}, modalViewState: {} });
      $ctrl.$onInit();
      expect($ctrl.viewState.advancedMode).toBe(true);
    });

    it('sets comparatorBound on modalViewState', function () {
      this.initialize({ command: {alarm: alarm}, serverGroup: { name: 'asg-v000'}, modalViewState: {} });
      $ctrl.$onInit();
      expect($ctrl.modalViewState.comparatorBound).toBe('max');
    });

    it('updates available metrics on initialization, triggers alarmUpdated once', function () {
      spyOn(cloudMetricsReader, 'listMetrics').and.returnValue($q.when([
        {
          namespace: 'AWS/EC2',
          name: 'CPUUtilization',
          dimensions: [ { name: 'AutoScalingGroupName', value: 'asg-v000' } ]
        },
        {
          namespace: 'AWS/EC2',
          name: 'NetworkIn',
          dimensions: [ { name: 'AutoScalingGroupName', value: 'asg-v000' } ]
        }
      ]));
      this.initialize({ command: {alarm: alarm}, serverGroup: { name: 'asg-v000'}, modalViewState: {} });
      spyOn($ctrl.alarmUpdated, 'next');
      $ctrl.$onInit();
      $scope.$digest();

      expect($ctrl.metrics.length).toBe(2);
      expect($ctrl.metrics.map(m => m.name)).toEqual(['CPUUtilization', 'NetworkIn']);
      expect($ctrl.viewState.selectedMetric).toBe($ctrl.metrics[0]);
      expect($ctrl.viewState.metricsLoaded).toBe(true);
      expect($ctrl.alarmUpdated.next.calls.count()).toBe(1);
    });
  });

  describe('thresholdChanged', function () {
    it ('updates first step when comparatorBound is max to new threshold, calls boundsChanged', function () {
      var bcCalled = false;
      let command = {
        alarm: {
          comparisonOperator: 'GreaterThanThreshold',
          threshold: 6,
        },
        step: {
          stepAdjustments: [
            {
              metricIntervalLowerBound: 1,
              metricIntervalUpperBound: 2
            },
            {
              metricIntervalLowerBound: 3,
              metricIntervalUpperBound: 4
            }
          ]
        }
      };
      let boundsChanged = () => bcCalled = true;

      this.initialize({
        command: command,
        serverGroup: { name: 'asg-v000'},
        modalViewState: { comparatorBound: 'max' },
        boundsChanged: boundsChanged });

      spyOn($ctrl.alarmUpdated, 'next');
      $ctrl.thresholdChanged();

      expect(command.step.stepAdjustments[0].metricIntervalLowerBound).toBe(6);
      expect(bcCalled).toBe(true);
      expect($ctrl.alarmUpdated.next.calls.count()).toBe(1);
    });
  });

  describe('metricChanged', function () {
    beforeEach(function () {
      alarm = {
        namespace: 'AWS/EC2',
        metricName: 'CPUUtilization',
        comparisonOperator: 'GreaterThanThreshold',
        dimensions: [ { name: 'AutoScalingGroupName', value: 'asg-v000' } ]
      };

      this.initialize({ command: {alarm: alarm}, serverGroup: { name: 'asg-v000'}, modalViewState: {} });
      spyOn(cloudMetricsReader, 'listMetrics').and.returnValue($q.when([
        {
          namespace: 'AWS/EC2',
          name: 'CPUUtilization',
          dimensions: [ { name: 'AutoScalingGroupName', value: 'asg-v000' } ]
        },
        {
          namespace: 'AWS/EC2',
          name: 'NetworkIn',
          dimensions: [ { name: 'AutoScalingGroupName', value: 'asg-v000' }, { name: 'sr', value: '71'} ]
        }
      ]));

      $ctrl.$onInit();
      $scope.$digest();
    });

    it('triggers alarmUpdated, updates alarm fields when new metric selected', function () {
      spyOn($ctrl.alarmUpdated, 'next');

      $ctrl.viewState.selectedMetric = $ctrl.metrics[1];
      $ctrl.metricChanged();
      expect(alarm.metricName).toBe('NetworkIn');
      expect($ctrl.alarmUpdated.next.calls.count()).toBe(1);
    });

    it('updates dimensions, available metrics when dimensions on selected metric are different', function () {
      spyOn($ctrl, 'updateAvailableMetrics');

      $ctrl.viewState.selectedMetric = $ctrl.metrics[1];
      $ctrl.metricChanged();

      expect(alarm.dimensions.length).toBe(2);
      expect($ctrl.updateAvailableMetrics.calls.count()).toBe(1);
    });

    it('clears namespace when not in advanced mode and selected metric is removed', function () {
      spyOn($ctrl.alarmUpdated, 'next');
      $ctrl.viewState.selectedMetric = null;
      $ctrl.metricChanged();
      expect(alarm.namespace).toBe(null);
      expect($ctrl.alarmUpdated.next.calls.count()).toBe(1);
    });

    it('does not clear namespace when in advanced mode and selected metric is removed', function () {
      spyOn($ctrl.alarmUpdated, 'next');
      $ctrl.viewState.advancedMode = true;
      $ctrl.viewState.selectedMetric = null;
      $ctrl.metricChanged();
      expect(alarm.namespace).toBe('AWS/EC2');
      expect($ctrl.alarmUpdated.next.calls.count()).toBe(1);
    });
  });

  describe('metric transformations', function () {
    beforeEach(function () {
      alarm = {
        namespace: 'AWS/EC2',
        metricName: 'CPUUtilization',
        comparisonOperator: 'GreaterThanThreshold',
        dimensions: [ { name: 'AutoScalingGroupName', value: 'asg-v000' } ]
      };

      this.initialize({ command: {alarm: alarm}, serverGroup: { name: 'asg-v000'}, modalViewState: {} });
      spyOn(cloudMetricsReader, 'listMetrics').and.returnValue($q.when([
        {
          namespace: 'AWS/EC2',
          name: 'CPUUtilization',
          dimensions: [ { name: 'AutoScalingGroupName', value: 'asg-v000' } ]
        },
        {
          namespace: 'AWS/EC2',
          name: 'NetworkIn',
          dimensions: [ { name: 'AutoScalingGroupName', value: 'asg-v000' }, { name: 'sr', value: '71'} ]
        },
        {
          namespace: 'AWS/EBS',
          name: 'somethingElse'
        }
      ]));

      $ctrl.$onInit();
      $scope.$digest();
    });

    it('adds label to each metric, sorts by label', function () {
      expect($ctrl.metrics.map(m => m.label)).toEqual([
        '(AWS/EBS) somethingElse', '(AWS/EC2) CPUUtilization', '(AWS/EC2) NetworkIn']);
    });

    it('adds default dimensions if not present', function () {
      expect($ctrl.metrics[0].dimensions).toEqual([]);
    });

    it('adds dimensionValues to each dimension', function () {
      expect($ctrl.metrics.map(m => m.dimensionValues)).toEqual(['', 'asg-v000', 'asg-v000, 71']);
    });

    it('adds advancedLabel based on presence of dimensions', function () {
      expect($ctrl.metrics.map(m => m.advancedLabel)).toEqual([
        'somethingElse', 'CPUUtilization (asg-v000)', 'NetworkIn (asg-v000, 71)'
      ]);
    });
  });

  describe('update available metrics', function () {
    it ('sets advanced mode when metrics fail to load', function () {
      alarm = {
        namespace: 'AWS/EC2',
        metricName: 'CPUUtilization',
        comparisonOperator: 'GreaterThanThreshold',
        dimensions: [ { name: 'AutoScalingGroupName', value: 'asg-v000' } ]
      };

      this.initialize({ command: {alarm: alarm}, serverGroup: { name: 'asg-v000'}, modalViewState: {} });
      spyOn(cloudMetricsReader, 'listMetrics').and.returnValue($q.reject(null));

      $ctrl.$onInit();
      $scope.$digest();

      expect($ctrl.viewState.metricsLoaded).toBe(true);
      expect($ctrl.viewState.advancedMode).toBe(true);
    });
  });

});
