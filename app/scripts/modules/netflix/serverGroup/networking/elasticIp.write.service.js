'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.aws.serverGroup.details.elasticIp.write.service', [
    require('../../../core/task/taskExecutor.js')
  ])
  .factory('elasticIpWriter', function (taskExecutor) {
    function associateElasticIpWithCluster(application, account, cluster, region, elasticIp) {
      return taskExecutor.executeTask({
        application: application,
        description: 'Associate Elastic IP with ' + cluster + (elasticIp.address ? ' (' + elasticIp.address + ')' : ''),
        job: [
          {
            'type': 'associateElasticIp',
            'account': account,
            'region': region,
            'cluster': cluster,
            'elasticIp': {
              'type': elasticIp.type,
              'address': elasticIp.address ? elasticIp.address : ''
            }
          }
        ]
      });
    }

    function disassociateElasticIpWithCluster(application, account, cluster, region, address) {
      return taskExecutor.executeTask({
        application: application,
        description: 'Disassociate Elastic IP with ' + cluster + ' (' + address + ')',
        job: [
          {
            type: 'disassociateElasticIp',
            account: account,
            region: region,
            cluster: cluster,
            elasticIp: {
              address: address
            }
          }
        ]
      });
    }

    return {
      associateElasticIpWithCluster: associateElasticIpWithCluster,
      disassociateElasticIpWithCluster: disassociateElasticIpWithCluster
    };
  });
