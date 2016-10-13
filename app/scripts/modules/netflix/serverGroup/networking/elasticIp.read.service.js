'use strict';

import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.aws.serverGroup.details.elasticIp.read.service', [API_SERVICE])
  .factory('elasticIpReader', function (API, $log) {
    function getElasticIpsForCluster(application, account, clusterName, region) {
      return API.one('applications', application).all('clusters').all(account).all(clusterName).one('elasticIps').all(region)
        .getList()
        .then(function (elasticIps) {
          return elasticIps;
        }, function(error) {
          $log.warn(error, 'error retrieving elastic ips');
          return [];
        });
    }

    return {
      getElasticIpsForCluster: getElasticIpsForCluster
    };
  });
