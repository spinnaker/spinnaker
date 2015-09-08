'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.aws.serverGroup.details.elasticIp.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
  ])
  .factory('elasticIpReader', function (Restangular, $exceptionHandler) {
    function getElasticIpsForCluster(application, account, clusterName, region) {
      return Restangular.one('applications', application).all('clusters').all(account).all(clusterName).one('elasticIps').all(region)
        .getList()
        .then(function (elasticIps) {
          return elasticIps;
        }, function(error) {
          $exceptionHandler(error, 'error retrieving elastic ips');
          return [];
        });
    }

    return {
      getElasticIpsForCluster: getElasticIpsForCluster
    };
  }).name;
