import { IComponentController, IComponentOptions, IRootScopeService, module } from 'angular';
import { Subject } from 'rxjs';

import { IScalingPolicyAlarm, ITargetTrackingConfiguration } from '@spinnaker/amazon';
import { ICloudMetricStatistics, IServerGroup } from '@spinnaker/core';

import { ITargetTrackingState } from './upsertTargetTracking.controller';

class TargetTrackingChartController implements IComponentController {
  public static $inject = ['$rootScope'];

  constructor(public $rootScope: IRootScopeService) {}

  public config: ITargetTrackingConfiguration;
  public serverGroup: IServerGroup;
  public state: ITargetTrackingState;
  public alarmUpdated: Subject<void>;
  public alarm: IScalingPolicyAlarm;

  public $onInit() {
    this.alarmUpdated = this.alarmUpdated || new Subject<void>();
    this.alarm = this.buildChartAlarm();
    this.synchronizeAlarm();
    this.alarmUpdated.subscribe(() => this.synchronizeAlarm());
  }

  public $onDestroy() {
    this.alarmUpdated.unsubscribe();
  }

  public onChartLoaded = (stats: ICloudMetricStatistics) => {
    if (this.state) {
      this.state.unit = stats.unit;
      this.alarmUpdated.next();
      this.$rootScope.$digest();
    }
  };

  private synchronizeAlarm(): void {
    const { config, alarm } = this;
    alarm.namespace = config.customizedMetricSpecification.namespace;
    alarm.metricName = config.customizedMetricSpecification.metricName;
    alarm.dimensions = config.customizedMetricSpecification.dimensions;
    alarm.statistic = config.customizedMetricSpecification.statistic;
    alarm.threshold = config.targetValue;
  }

  private buildChartAlarm(): IScalingPolicyAlarm {
    return {
      alarmName: null,
      alarmArn: null,
      metricName: null,
      namespace: null,
      statistic: 'Average',
      dimensions: [],
      period: 60,
      threshold: this.config.targetValue,
      comparisonOperator: 'GreaterThanThreshold',
      okactions: [],
      insufficientDataActions: [],
      alarmActions: [],
      evaluationPeriods: null,
      alarmDescription: null,
      unit: null,
    };
  }
}

const component: IComponentOptions = {
  bindings: {
    config: '<',
    serverGroup: '<',
    state: '=',
    alarmUpdated: '<',
  },
  controller: TargetTrackingChartController,
  template: `
    <metric-alarm-chart
      alarm="$ctrl.alarm"
      server-group="$ctrl.serverGroup"
      alarm-updated="$ctrl.alarmUpdated"
      on-chart-loaded="$ctrl.onChartLoaded">
    </metric-alarm-chart>
  `,
};

export const TARGET_TRACKING_CHART_COMPONENT = 'spinnaker.titus.scalingPolicy.targetTracking.chart.component';
module(TARGET_TRACKING_CHART_COMPONENT, []).component('titusTargetTrackingChart', component);
