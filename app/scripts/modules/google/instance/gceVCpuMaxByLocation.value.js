'use strict';

let angular = require('angular');

/**
 * Zones that support Haswell and Ivy Bridge processors can support custom machine types up to 32 vCPUs.
 * Zones that support Sandy Bridge processors can support up to 16 vCPUs.
 * This list should be kept in sync with the corresponding list in clouddriver:
 * @link { https://github.com/spinnaker/clouddriver/blob/master/clouddriver-google/src/main/groovy/com/netflix/spinnaker/clouddriver/google/deploy/validators/StandardGceAttributeValidator.groovy }
 */
module.exports = angular.module('spinnaker.gce.instance.vCpuMaxByLocation.value', [
  require('../../core/utils/lodash.js')
])
  .constant('gceVCpuMaxByLocation', {
    'us-east1-b': 32,
    'us-east1-c': 32,
    'us-east1-d': 32,
    'us-central1-a': 16,
    'us-central1-b': 32,
    'us-central1-c': 32,
    'us-central1-f': 32,
    'us-west1-a': 32,
    'us-west1-b': 32,
    'europe-west1-b': 16,
    'europe-west1-c': 32,
    'europe-west1-d': 32,
    'asia-east1-a': 32,
    'asia-east1-b': 32,
    'asia-east1-c': 32,
    'us-east1': 32,
    'us-central1': 32,
    'us-west1': 32,
    'europe-west1': 16,
    'asia-east1': 32,
  });
