'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.aws.serverGroup.details.scalingPolicy.alarm.configurer', [
    require('../../../../../../core/utils/lodash.js'),
    require('../../../../../../core/utils/rx.js'),
    require('../../../../../../core/config/settings.js'),
    require('../../../../../../core/serverGroup/metrics/cloudMetrics.read.service.js'),
    require('./dimensionsEditor.component.js'),
  ])
  .component('awsAlarmConfigurer', {
    bindings: {
      command: '=',
      modalViewState: '=',
      serverGroup: '<',
      boundsChanged: '&',
    },
    templateUrl: require('./alarmConfigurer.component.html'),
    controller: function (cloudMetricsReader, _, rx, settings) {

      // AWS does not provide an API to get this, so we're baking it in. If you use custom namespaces, add them to
      // the settings.js block for aws as an array, e.g. aws { metrics: { customNamespaces: ['myns1', 'other'] } }
      this.namespaces =
        _.get(settings, 'providers.aws.metrics.customNamespaces', []).concat([
          'AWS/AutoScaling',
          'AWS/Billing',
          'AWS/CloudFront',
          'AWS/CloudSearch',
          'AWS/Events',
          'AWS/DynamoDB',
          'AWS/ECS',
          'AWS/ElastiCache',
          'AWS/EBS',
          'AWS/EC2',
          'AWS/ELB',
          'AWS/ElasticMapReduce',
          'AWS/ES',
          'AWS/Kinesis',
          'AWS/Lambda',
          'AWS/ML',
          'AWS/OpsWorks',
          'AWS/Redshift',
          'AWS/RDS',
          'AWS/Route53',
          'AWS/SNS',
          'AWS/SQS',
          'AWS/S3',
          'AWS/SWF',
          'AWS/StorageGateway',
          'AWS/WAF',
          'AWS/WorkSpaces',
        ]);

      this.statistics = [ 'Average', 'Maximum', 'Minimum', 'SampleCount', 'Sum' ];

      this.comparators = [
        { label: '>=', value: 'GreaterThanOrEqualToThreshold' },
        { label: '>', value: 'GreaterThanThreshold' },
        { label: '<=', value: 'LessThanOrEqualToThreshold' },
        { label: '<', value: 'LessThanThreshold' },
      ];

      this.periods = [
        { label: '1 minute', value: 60 },
        { label: '5 minutes', value: 60 * 5 },
        { label: '15 minutes', value: 60 * 15 },
        { label: '1 hour', value: 60 * 60 },
        { label: '4 hours', value: 60 * 60 * 4},
        { label: '1 day', value: 60 * 60 * 24 },
      ];

      this.viewState = {
        advancedMode: false,
        metricsLoaded: false,
        selectedMetric: null,
        noDefaultMetrics: false,
      };

      this.alarmUpdated = new rx.Subject();
      this.namespaceUpdated = new rx.Subject();

      this.thresholdChanged = () => {
        let source = this.modalViewState.comparatorBound === 'max' ? 'metricIntervalLowerBound' : 'metricIntervalUpperBound';
        if (this.command.step) {
          // always set the first step at the alarm threshold
          this.command.step.stepAdjustments[0][source] = this.command.alarm.threshold;
        }
        this.boundsChanged();
        this.alarmUpdated.onNext();
      };

      let convertDimensionsToObject = () => {
        return this.alarm.dimensions.reduce((acc, dimension) => {
          acc[dimension.name] = dimension.value;
          return acc;
        }, {});
      };

      // used to determine if dimensions have changed when selecting a metric
      function dimensionsToString(metric) {
        let dimensions = metric.dimensions || [];
        return dimensions.map(d => [d.name, d.value].join(':')).join(',');
      }

      this.metricChanged = (forceUpdateStatistics) => {
        if (!this.viewState.metricsLoaded) {
          return;
        }
        let alarm = this.alarm;
        if (this.viewState.advancedMode) {
          this.alarmUpdated.onNext();
          return;
        }
        if (this.viewState.selectedMetric) {
          let selected = this.viewState.selectedMetric,
              dimensionsChanged = selected && dimensionsToString(alarm) !== dimensionsToString(selected),
              alarmUpdated = alarm.metricName !== selected.name || alarm.namespace !== selected.namespace ||
                             dimensionsChanged;
          alarm.metricName = selected.name;
          alarm.namespace = selected.namespace;
          if (dimensionsChanged) {
            alarm.dimensions = selected.dimensions;
            this.updateAvailableMetrics();
          }
          if (alarmUpdated || forceUpdateStatistics) {
            this.alarmUpdated.onNext();
          }
        } else {
          alarm.namespace = null;
          alarm.metricName = null;
          this.alarmUpdated.onNext();
        }
      };

      this.periodChanged = () => this.alarmUpdated.onNext();

      this.namespaceChanged = () => {
        this.namespaceUpdated.onNext();
        this.updateAvailableMetrics();
      };

      this.advancedMode = () => {
        this.viewState.advancedMode = true;
      };

      this.simpleMode = () => {
        this.alarm.dimensions = [ { name: 'AutoScalingGroupName', value: this.serverGroup.name }];
        this.viewState.advancedMode = false;
        this.updateAvailableMetrics();
      };

      function dimensionSorter(a, b) {
        return a.name.localeCompare(b.name);
      }

      function transformAvailableMetric(metric) {
        metric.label = `(${metric.namespace}) ${metric.name}`;
        metric.dimensions = metric.dimensions || [];
        metric.dimensionValues = metric.dimensions.sort(dimensionSorter).map(d => d.value).join(', ');
        if (metric.dimensions.length) {
          metric.advancedLabel = `${metric.name} (${metric.dimensionValues})`;
        } else {
          metric.advancedLabel = metric.name;
        }
      }

      this.updateAvailableMetrics = () => {
        let alarm = this.alarm;
        let dimensions = convertDimensionsToObject();
        if (this.viewState.advancedMode) {
          dimensions.namespace = alarm.namespace;
        }

        cloudMetricsReader.listMetrics('aws', this.serverGroup.account, this.serverGroup.region, dimensions).then(
          (results) => {
            results = results || [];
            this.viewState.metricsLoaded = true;
            results.forEach(transformAvailableMetric);
            this.metrics = results.sort((a, b) => a.label.localeCompare(b.label));
            let currentDimensions = alarm.dimensions.sort(dimensionSorter).map(d => d.value).join(', ');
            let [selected] = this.metrics.filter(metric =>
              metric.name === alarm.metricName && metric.namespace === alarm.namespace &&
              metric.dimensionValues === currentDimensions
            );
            if (!results.length && !this.viewState.advancedMode) {
              this.viewState.noDefaultMetrics = true;
              alarm.namespace = alarm.namespace || this.namespaces[0];
              this.advancedMode();
            }
            if (selected) {
              this.viewState.selectedMetric = selected;
            }
            this.metricChanged();
          })
        .catch(() => {
            this.viewState.metricsLoaded = true;
            this.advancedMode();
          });
      };

      this.alarmComparatorChanged = () => {
        let previousComparatorBound = this.modalViewState.comparatorBound;
        this.modalViewState.comparatorBound = this.alarm.comparisonOperator.indexOf('Greater') === 0 ? 'max' : 'min';
        if (previousComparatorBound && this.modalViewState.comparatorBound !== previousComparatorBound && this.command.step) {
          this.command.step.stepAdjustments = [ {scalingAdjustment: 1} ];
          this.thresholdChanged();
        }
        this.metricChanged();
      };

      let initializeMode = () => {
        let dimensions = this.alarm.dimensions;
        if (!dimensions || !dimensions.length || dimensions.length > 1 ||
          dimensions[0].name !== 'AutoScalingGroupName' || dimensions[0].value !== this.serverGroup.name) {
          this.advancedMode();
        }
      };

      this.$onInit = () => {
        this.alarm = this.command.alarm;
        initializeMode();
        this.updateAvailableMetrics();
        this.alarmComparatorChanged();
        this.alarmUpdated.onNext();
      };

    }
  });
