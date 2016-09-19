'use strict';

function createServerGroupJob({ appName, account, cloudProvider, image, region, zone }) {
  let availabilityZones = {};
  availabilityZones[region] = [zone];

  return {
    application: appName,
    credentials: account,
    region: region,
    zone: zone,
    regional: false,
    network: 'default',
    capacity: {'min': 1, 'max': 1, 'desired': 1},
    backendServiceMetadata: [],
    instanceMetadata: {},
    tags: [],
    strategy: '',
    preemptible: false,
    automaticRestart: true,
    onHostMaintenance: 'MIGRATE',
    serviceAccountEmail: 'default',
    authScopes: ['cloud.useraccounts.readonly', 'devstorage.read_only', 'logging.write', 'monitoring.write'],
    cloudProvider: cloudProvider,
    availabilityZones: availabilityZones,
    image: image,
    subnet: '',
    securityGroups: [],
    instanceType: 'f1-micro',
    disks: [{'type': 'pd-ssd', 'sizeGb': 10}],
    targetSize: 1,
    type: 'createServerGroup',
    disableTraffic: false,
    account: account,
    user: '[anonymous]'
  };
}

module.exports = createServerGroupJob;
