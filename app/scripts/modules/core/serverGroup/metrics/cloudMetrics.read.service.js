'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.aws.serverGroup.details.scalingPolicy.read.service', [
    require('../../config/settings.js'),
  ])
  .factory('cloudMetricsReader', function($http, settings) {

    function listMetrics(provider, account, region, filters) {
      return $http.get(`${settings.gateUrl}/cloudMetrics/${provider}/${account}/${region}`, { params: filters })
        .then(response => response.data);
    }

    function getMetricStatistics(provider, account, region, name, filters) {
      return $http.get(`${settings.gateUrl}/cloudMetrics/${provider}/${account}/${region}/${name}/statistics`, { params: filters })
        .then(response => response.data);
    }

    return {
      listMetrics: listMetrics,
      getMetricStatistics: getMetricStatistics,
    };
  });
