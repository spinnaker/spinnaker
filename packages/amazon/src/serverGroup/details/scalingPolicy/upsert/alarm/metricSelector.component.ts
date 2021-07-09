import { IComponentOptions, IController, module } from 'angular';
import { Dictionary } from 'lodash';
import { Subject } from 'rxjs';

import { CloudMetricsReader, ICloudMetricDescriptor, IMetricAlarmDimension, IServerGroup } from '@spinnaker/core';

import { IConfigurableMetric } from '../../ScalingPolicyWriter';
import { AWSProviderSettings } from '../../../../../aws.settings';
import { NAMESPACES } from './namespaces';

export interface IMetricOption extends ICloudMetricDescriptor {
  label: string;
  dimensionValues: string;
}

export interface IMetricEditorState {
  advancedMode: boolean;
  metricsLoaded: boolean;
  metrics: IMetricOption[];
  selectedMetric: ICloudMetricDescriptor;
  noDefaultMetrics?: boolean;
}

const dimensionSorter = (a: IMetricAlarmDimension, b: IMetricAlarmDimension): number => {
  return a.name.localeCompare(b.name);
};

export class MetricSelectorController implements IController {
  public alarmUpdated: Subject<void>;
  public namespaceUpdated = new Subject();

  public alarm: IConfigurableMetric;
  public namespaces = (AWSProviderSettings?.metrics?.customNamespaces ?? []).concat(NAMESPACES);
  public state: IMetricEditorState;
  public serverGroup: IServerGroup;

  public $onInit(): void {
    this.state = {
      advancedMode: false,
      metricsLoaded: false,
      metrics: [],
      selectedMetric: null,
    };
    const dimensions = this.alarm.dimensions;
    if (
      !dimensions ||
      !dimensions.length ||
      dimensions.length > 1 ||
      dimensions[0].name !== 'AutoScalingGroupName' ||
      dimensions[0].value !== this.serverGroup.name
    ) {
      this.state.advancedMode = true;
    }
    this.updateAvailableMetrics();
    this.alarmUpdated.next();
  }

  public simpleMode(): void {
    this.alarm.dimensions = [{ name: 'AutoScalingGroupName', value: this.serverGroup.name }];
    this.state.advancedMode = false;
    this.updateAvailableMetrics();
  }

  public advancedMode(): void {
    this.state.advancedMode = true;
  }

  public updateAvailableMetrics(): void {
    const { alarm } = this;
    const dimensions = this.convertDimensionsToObject();
    if (this.state.advancedMode) {
      dimensions.namespace = alarm.namespace;
    }

    CloudMetricsReader.listMetrics('aws', this.serverGroup.account, this.serverGroup.region, dimensions)
      .then((results) => {
        results = results || [];
        this.state.metricsLoaded = true;
        this.state.metrics = results
          .map((r) => this.buildMetricOption(r))
          .sort((a, b) => a.label.localeCompare(b.label));
        const currentDimensions = alarm.dimensions
          .sort(dimensionSorter)
          .map((d: IMetricAlarmDimension) => d.value)
          .join(', ');
        const selected = this.state.metrics.find(
          (metric) =>
            metric.name === alarm.metricName &&
            metric.namespace === alarm.namespace &&
            metric.dimensionValues === currentDimensions,
        );
        if (!results.length && !this.state.advancedMode) {
          this.state.noDefaultMetrics = true;
          alarm.namespace = alarm.namespace || this.namespaces[0];
          this.advancedMode();
        }
        if (selected) {
          this.state.selectedMetric = selected;
        } else {
          // If metricName is blank (new policy), try to find a CPU metric or select the first option instead of sitting on the invalid blank option
          if (!alarm.metricName && this.state.metrics.length) {
            this.state.selectedMetric =
              this.state.metrics.find((metric) => metric.name.match('CPUUtilization')) || this.state.metrics[0];
          }
        }
        this.metricChanged();
      })
      .catch(() => {
        this.state.metricsLoaded = true;
        this.advancedMode();
      });
  }

  private buildMetricOption(metric: ICloudMetricDescriptor): IMetricOption {
    const option: IMetricOption = {
      label: `(${metric.namespace}) ${metric.name}`,
      dimensions: [],
      dimensionValues: metric.dimensions
        .sort(dimensionSorter)
        .map((d) => d.value)
        .join(', '),
      ...metric,
    };
    return option;
  }

  private convertDimensionsToObject(): Dictionary<string> {
    return this.alarm.dimensions.reduce((acc: Dictionary<string>, dimension: IMetricAlarmDimension) => {
      acc[dimension.name] = dimension.value;
      return acc;
    }, {} as Dictionary<string>);
  }

  // used to determine if dimensions have changed when selecting a metric
  private dimensionsToString(dimensions: IMetricAlarmDimension[] = []) {
    return dimensions.map((d) => [d.name, d.value].join(':')).join(',');
  }

  public metricChanged(forceUpdateStatistics = false): void {
    const { alarm } = this;

    if (!this.state.metricsLoaded) {
      return;
    }
    if (this.state.advancedMode) {
      this.alarmUpdated.next();
      return;
    }
    if (this.state.selectedMetric) {
      const selected = this.state.selectedMetric;
      const dimensionsChanged =
        selected && this.dimensionsToString(alarm.dimensions) !== this.dimensionsToString(selected.dimensions);
      const alarmUpdated =
        alarm.metricName !== selected.name || alarm.namespace !== selected.namespace || dimensionsChanged;
      alarm.metricName = selected.name;
      alarm.namespace = selected.namespace;
      if (dimensionsChanged) {
        alarm.dimensions = selected.dimensions;
        this.updateAvailableMetrics();
      }
      if (alarmUpdated || forceUpdateStatistics) {
        this.alarmUpdated.next();
      }
    } else {
      alarm.namespace = null;
      alarm.metricName = null;
      this.alarmUpdated.next();
    }
  }
}

const component: IComponentOptions = {
  bindings: {
    alarm: '<',
    serverGroup: '<',
    alarmUpdated: '<',
  },
  controller: MetricSelectorController,
  template: `
      <div class="text-center" style="display: inline-block; width: 100px; margin-top: 7px" ng-if="!$ctrl.state.metricsLoaded">
        <loading-spinner size="'small'"></loading-spinner>
      </div>
      <div style="display: inline-block; width: 500px" ng-if="$ctrl.state.metricsLoaded">
        <select class="form-control input-sm"
                required
                ng-model="$ctrl.state.selectedMetric"
                ng-change="$ctrl.metricChanged()"
                ng-if="!$ctrl.state.advancedMode"
                ng-options="metric as metric.label for metric in $ctrl.state.metrics">
        </select>
        <select class="form-control input-sm"
                required
                ng-model="$ctrl.alarm.namespace"
                ng-change="$ctrl.namespaceChanged()"
                ng-if="$ctrl.state.advancedMode"
                ng-options="namespace for namespace in $ctrl.namespaces">
        </select>
        <div style="display: inline-block; width: 300px; position: relative; top: -3px"
             ng-if="$ctrl.state.advancedMode">
          <input type="text" class="form-control input-sm no-spel" style="width: 100%"
                 required
                 uib-typeahead="option.name as option.name for option in $ctrl.state.metrics | filter: $viewValue"
                 typeahead-show-hint="true"
                 typeahead-min-length="0"
                 typeahead-editable="true"
                 typeahead-on-select="$ctrl.metricChanged()"
                 ng-change="$ctrl.metricChanged()"
                 ng-model-options="{ updateOn: 'default blur', debounce: { 'default': 300, 'blur': 0 } }"
                 ng-model="$ctrl.alarm.metricName"
                 placeholder="name"/>
        </div>
        <a href class="small"
           ng-if="!$ctrl.state.advancedMode"
           ng-click="$ctrl.advancedMode()">
          Search all metrics <help-field key="aws.scalingPolicy.search.all"></help-field>
        </a>
        <span class="input-label" style="margin-left: 5px"
              ng-if="$ctrl.state.advancedMode && $ctrl.state.metrics.length === 0">
          <strong>Note:</strong> no metrics found for selected namespace + dimensions
        </span>
        <div style="padding-left: 5px;">
          <a href class="small"
             ng-if="$ctrl.state.advancedMode && !$ctrl.state.noDefaultMetrics"
             ng-click="$ctrl.simpleMode()">
            Only show metrics for this auto scaling group <help-field key="aws.scalingPolicy.search.restricted"></help-field>
          </a>
        </div>
        <div ng-if="$ctrl.state.advancedMode">
          <dimensions-editor alarm="$ctrl.alarm"
                             server-group="$ctrl.serverGroup"
                             namespace-updated="$ctrl.namespaceUpdated"
                             update-available-metrics="$ctrl.updateAvailableMetrics()"></dimensions-editor>
        </div>
  `,
};

export const METRIC_SELECTOR_COMPONENT = 'spinnaker.amazon.scalingPolicy.alarm.metric.editor';
module(METRIC_SELECTOR_COMPONENT, []).component('awsMetricSelector', component);
