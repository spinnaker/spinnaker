'use strict';


let angular = require('angular');

module.exports = angular
  .module('spinnaker.vpc.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('utils/lodash.js'),
  ])
  .factory('vpcReader', function ($q, Restangular, infrastructureCaches ) {

    function listVpcs() {
      return Restangular.all('vpcs')
        .withHttpConfig({cache: infrastructureCaches.vpcs})
        .getList();
    }

    return {
      listVpcs: listVpcs
    };

  }).name;
