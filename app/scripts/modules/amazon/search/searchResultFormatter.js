'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.search.searchResultFormatter', [
    require('../vpc/vpc.read.service.js'),
  ])
  .factory('awsSearchResultFormatter', function(vpcReader) {
    return {
      securityGroups: function(entry) {
        return vpcReader.getVpcName(entry.vpcId).then(function (vpcName) {
          let region = vpcName ? entry.region + ' - ' + vpcName.toLowerCase() : entry.region;
          return entry.name + ' (' + region + ')';
        });
      }
    };
  });
