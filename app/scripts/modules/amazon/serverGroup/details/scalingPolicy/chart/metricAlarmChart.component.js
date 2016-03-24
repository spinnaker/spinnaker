'use strict';

const angular = require('angular');

require('style!n3-charts/build/LineChart.css');
require('./metricAlarmChart.component.less');

module.exports = angular
  .module('spinnaker.aws.serverGroup.details.scalingPolicy.metricAlarmChart.component', [
    require('../../../../../core/serverGroup/metrics/cloudMetrics.read.service.js'),
    require('../../../../../core/utils/lodash.js'),
    require('../../../../../core/utils/rx.js'),
    require('exports?"n3-line-chart"!n3-charts/build/LineChart.js'),
  ])
  .component('metricAlarmChart', {
    bindings: {
      alarm: '=',
      serverGroup: '=',
      alarmUpdated: '=?', // rx.Observable - onNext will update the graph
      ticks: '=?', // object - sets number of x/y ticks on graph; defaults to { x: 6, y: 3 }
      margins: '=?', // object - defaults to { top: 5, left: 5 }
    },
    templateUrl: require('./metricAlarmChart.component.html'),
    controller: function(cloudMetricsReader, _, rx) {

      // converts alarm into parameters used to retrieve statistic data
      let getFilterParameters = () => {
        let alarm = this.alarm;
        let base = {
          namespace: alarm.namespace,
          statistics: alarm.statistic,
          period: alarm.period,
        };
        return alarm.dimensions.reduce((acc, dimension) => {
          acc[dimension.name] = dimension.value;
          return acc;
        }, base);
      };

      let initializeStatistics = () => {
        let start = new Date(new Date().getTime() - 24 * 60 * 60 * 1000),
            end = new Date(),
            threshold = this.alarm.threshold || 0,
            topline = this.alarm.comparisonOperator.indexOf('Less') === 0 ? threshold * 3 : 0;

        /**
         * Draw four lines:
         *  threshold: configured alarm threshold (red line)
         *  datapoints: metric statistics from cloud provider
         *  baseline: zero - forces y-axis to start at zero
         *  topline: zero (if alarm breaches upward) or 3 * threshold (if alarm breaches downward) - forces initial
         *           graph height to some approximate value to avoid excessive scaling when datapoints are populated
         */
        this.chartData = {
          loading: true,
          noData: false, // flag set when server error occurs or no data available from server
          threshold: [
            { val: threshold, timestamp: start },
            { val: threshold, timestamp: end },
          ],
          datapoints: [],
          baseline: [
            { val: 0, timestamp: start },
            { val: 0, timestamp: end },
          ],
          topline: [
            { val: topline, timestamp: start },
            { val: topline, timestamp: end },
          ]
        };
      };

      let updateChartData = () => {
        cloudMetricsReader.getMetricStatistics(this.serverGroup.type, this.serverGroup.account, this.serverGroup.region, this.alarm.metricName, getFilterParameters())
          .then((stats) => {
            this.chartData.loading = false;
            this.chartData.noData = false;
            if (stats.datapoints && stats.datapoints.length) {
              stats.datapoints.forEach(point => {
                point.timestamp = new Date(point.timestamp); // convert to Date object for graph
              });
              this.chartData.datapoints = stats.datapoints;
            } else {
              this.chartData.noData = true;
            }
          })
          .catch(() => {
            this.chartData.noData = true;
            this.chartData.loading = false;
          });
      };

      let configureChart = () => {
        let statKey = _.camelCase(this.alarm.statistic);
        initializeStatistics(statKey);

        let ticks = this.ticks || {x: 6, y: 3};

        this.chartOptions = {
          margin: this.margins || {top: 5, left: 5},
          drawLegend: false,
          series: [
            {
              axis: 'y',
              dataset: 'datapoints',
              key: statKey,
              label: `${this.alarm.metricName} (${statKey})`,
              color: 'hsla(88, 48%, 48%, 1)',
              type: ['line'],
              id: 'alarmData'
            },
            {
              axis: 'y',
              dataset: 'threshold',
              key: 'val',
              label: 'alarm threshold',
              color: 'hsl(343, 79%, 32%)',
              type: ['line'],
              id: 'threshold',
            },
            {
              axis: 'y',
              dataset: 'baseline',
              key: 'val',
              label: '',
              color: 'transparent',
              type: ['line'],
              id: 'baseline'
            },
            {
              axis: 'y',
              dataset: 'topline',
              key: 'val',
              label: '',
              color: 'transparent',
              type: ['line'],
              id: 'topline'
            }
          ],
          axes: {
            x: { key: 'timestamp', type: 'date', ticks: ticks.x },
            y:  { ticks: ticks.y },
            x2: { ticks: 0 }, // hide right hand x-axis labels
            y2: { ticks: 0 } // hide top y-axis labels
          }
        };
        updateChartData();
      };

      this.$onInit = () => {
        configureChart();
        if (!this.alarmUpdated) {
          this.alarmUpdatedCreated = true;
          this.alarmUpdated = new rx.Subject();
        }
        this.alarmUpdated.subscribe(() => configureChart());
      };

      // HACK: This won't do anything until a fix for https://github.com/angular/angular.js/issues/14020 is released.
      // It will probably lead to some very small memory leaks, and possibly some exceptions that the user will not see.
      this.$onDestroy = () => {
        if (this.alarmUpdatedCreated) {
          this.alarmUpdated.dispose();
        }
      };
    }
  });
