'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.autoscalingPolicy.metricSettings.component', [
    require('../../../../core/utils/lodash.js')
  ])
  .component('gceAutoscalingPolicyMetricSettings', {
    bindings: {
      policy: '=',
      showNoMetricsWarning: '=',
    },
    templateUrl: require('./metricSettings.component.html'),
    controller: function (_) {
      let multipleAllowedFor = {
        cpuUtilization: false,
        loadBalancingUtilization: false,
        customMetricUtilizations: true
      };

      let metricTypes = Object.keys(multipleAllowedFor);

      this.targetTypesToDisplayMap = {
        'GAUGE' : 'Gauge',
        'DELTA_PER_SECOND' : 'Delta / second',
        'DELTA_PER_MINUTE' : 'Delta / minute'
      };

      this.addMetric = (metricType) => {
        if (multipleAllowedFor[metricType]) {
          this.policy[metricType] = this.policy[metricType] || [];
          this.policy[metricType].push({});
        } else if (emptyOrUndefined(this.policy[metricType])) {
          this.policy[metricType] = { utilizationTarget: null };
        }
      };

      this.deleteMetric = (metricType, index) => {
        if (multipleAllowedFor[metricType]) {
          this.policy[metricType].splice(index, 1);
        } else {
          // sending an empty object to the API deletes the policy.
          this.policy[metricType] = {};
        }
      };

      this.showMetric = (metricType) => {
        let metric = this.policy[metricType];
        // should not show policy form if the policy is undefined or an empty object.
        return !emptyOrUndefined(metric);
      };

      this.showNoMetricsWarning = () => {
        return _.every(metricTypes.map(type => {
          return _.some([
            multipleAllowedFor[type] && !_.get(this.policy, [type, 'length']),
            emptyOrUndefined(this.policy[type])]);
        }));
      };

      this.setUtilizationTargetFromDisplay = (metricType, value) => {
        this.policy[metricType].utilizationTarget = value / 100;
      };

      this.initializeTargetDisplay = (metricType) => {
        this[`${metricType}TargetDisplay`] = safeDecimalToPercent(this.policy[metricType].utilizationTarget);
      };

      function safeDecimalToPercent (value) {
        if (value === 0) {
          return 0;
        }
        return value ? Math.round(value * 100) : undefined;
      }

      function emptyOrUndefined(value) {
        return _.isEqual(value, {}) || _.isUndefined(value);
      }
    }
  });
