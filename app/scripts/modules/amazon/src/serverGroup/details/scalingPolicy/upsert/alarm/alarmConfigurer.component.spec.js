'use strict';

describe('Component: alarmConfigurer', function () {
  let $ctrl;

  beforeEach(window.module(require('./alarmConfigurer.component').name));

  beforeEach(
    window.inject(function ($componentController) {
      this.initialize = (bindings) => {
        $ctrl = $componentController('awsAlarmConfigurer', {}, bindings);
      };
    }),
  );

  describe('thresholdChanged', function () {
    it('updates first step when comparatorBound is max to new threshold, calls boundsChanged', function () {
      let bcCalled = false;
      const command = {
        alarm: {
          comparisonOperator: 'GreaterThanThreshold',
          threshold: 6,
        },
        step: {
          stepAdjustments: [
            {
              metricIntervalLowerBound: 1,
              metricIntervalUpperBound: 2,
            },
            {
              metricIntervalLowerBound: 3,
              metricIntervalUpperBound: 4,
            },
          ],
        },
      };
      const boundsChanged = () => (bcCalled = true);

      this.initialize({
        command: command,
        serverGroup: { name: 'asg-v000' },
        modalViewState: { comparatorBound: 'max' },
        boundsChanged: boundsChanged,
      });

      spyOn($ctrl.alarmUpdated, 'next');
      $ctrl.thresholdChanged();

      expect(command.step.stepAdjustments[0].metricIntervalLowerBound).toBe(6);
      expect(bcCalled).toBe(true);
      expect($ctrl.alarmUpdated.next.calls.count()).toBe(1);
    });
  });
});
