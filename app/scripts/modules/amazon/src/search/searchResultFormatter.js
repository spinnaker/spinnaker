'use strict';

const angular = require('angular');

import { VPC_READ_SERVICE } from '../vpc/vpc.read.service';

module.exports = angular
  .module('spinnaker.amazon.search.searchResultFormatter', [
    VPC_READ_SERVICE,
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
