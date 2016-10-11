'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.serverGroup.details.networking.controller', [
  require('core/confirmationModal/confirmationModal.service.js'),
  require('./elasticIp.read.service.js'),
  require('./elasticIp.controller.js'),
  require('./ip.sort.filter.js'),
])
  .controller('networkingCtrl', function ($uibModal, elasticIpReader) {

    let getElasticIpsForCluster = () => {
      var serverGroup = this.serverGroup;
      var application = this.application;
      elasticIpReader.getElasticIpsForCluster(application.name, serverGroup.account, serverGroup.cluster, serverGroup.region)
        .then((elasticIps) => {
          this.elasticIps = elasticIps ? elasticIps : [];
        });
    };

    getElasticIpsForCluster();

    this.associateElasticIp = function associateElasticIp() {
      $uibModal.open({
        templateUrl: require('./details/associateElasticIp.html'),
        controller: 'ElasticIpCtrl as ctrl',
        resolve: {
          application: () => this.application,
          serverGroup: () => this.serverGroup,
          elasticIp: () => { return { type: 'standard' }; },
          onTaskComplete: () => getElasticIpsForCluster
        }
      });
    };

    this.disassociateElasticIp = (address) => {
      $uibModal.open({
        templateUrl: require('./details/disassociateElasticIp.html'),
        controller: 'ElasticIpCtrl as ctrl',
        resolve: {
          application: () => this.application,
          serverGroup: () => this.serverGroup,
          elasticIp: () => _.find(this.elasticIps, (elasticIp) => elasticIp.address === address),
          onTaskComplete: () => getElasticIpsForCluster
        }
      });
    };
  });
