'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.customInstanceBuilder.gce.service', [
  require('../../../../../core/utils/lodash.js')
])
  .factory('gceCustomInstanceBuilderService', function(_) {
    /**
     * Zones that support Haswell and Ivy Bridge processors can support custom machine types up to 32 vCPUs.
     * Zones that support Sandy Bridge processors can support up to 16 vCPUs.
     * This list should be kept in sync with the corresponding list in clouddriver:
     * @link { https://github.com/spinnaker/clouddriver/blob/master/clouddriver-google/src/main/groovy/com/netflix/spinnaker/clouddriver/google/deploy/validators/StandardGceAttributeValidator.groovy }
    */
    let cpuMaxByLocation = {
      'us-east1-b': 32,
      'us-east1-c': 32,
      'us-east1-d': 32,
      'us-central1-a': 16,
      'us-central1-b': 32,
      'us-central1-c': 32,
      'us-central1-f': 32,
      'europe-west1-b': 16,
      'europe-west1-c': 32,
      'europe-west1-d': 32,
      'asia-east1-a': 32,
      'asia-east1-b': 32,
      'asia-east1-c': 32,
      'us-east1': 32,
      'us-central1': 32,
      'europe-west1': 32,
      'asia-east1': 32
    };

    function vCpuCountForLocationIsValid(vCpuCount, location) {
      return vCpuCount <= cpuMaxByLocation[location];
    }

    /*
    * Above 1, vCPU count must be even.
    * */
    function numberOfVCpusIsValid(vCpuCount) {
      if (vCpuCount === 1) {
        return true;
      }
      return vCpuCount % 2 === 0;
    }

    function generateValidVCpuListForLocation(location) {
      let max = cpuMaxByLocation[location];
      return [ 1, ..._.range(2, max, 2), max ];
    }

    /*
    * Memory per vCPU must be between .9 GB and 6.5 GB
    * Total memory must be a multiple of 256 MB.
    * */
    function minMemoryForVCpuCount(vCpuCount) {
      return Math.ceil((0.9 * vCpuCount) * 4) / 4;
    }

    function maxMemoryForVCpuCount(vCpuCount) {
      return 6.5 * vCpuCount;
    }

    function generateValidMemoryListForVCpuCount(vCpuCount) {
      let min = minMemoryForVCpuCount(vCpuCount);
      let max = maxMemoryForVCpuCount(vCpuCount);
      return [ ..._.range(min, max, .25), max ];
    }

    function memoryIsValid(totalMemory, vCpuCount) {
      let min = minMemoryForVCpuCount(vCpuCount);
      let max = maxMemoryForVCpuCount(vCpuCount);
      return _.inRange(totalMemory, min, max) || totalMemory === max;
    }

    /*
    * In the API, you must always provide memory in MB units.
    * Format: custom-NUMBER_OF_CPUS-AMOUNT_OF_MEMORY
    * */
    function generateInstanceTypeString(vCpuCount, totalMemory) {
      let memoryInMbs = Number(totalMemory) * 1024;
      return `custom-${vCpuCount}-${memoryInMbs}`;
    }

    function parseInstanceTypeString(instanceTypeString) {
      let [ vCpuCount, memoryInMbs ] = _(instanceTypeString.split('-'))
        .takeRight(2)
        .map(value => Number(value))
        .value();

      let memory = memoryInMbs / 1024;

      return { vCpuCount, memory };
    }

    function customInstanceChoicesAreValid(vCpuCount, totalMemory, location) {
      return _.every([
        numberOfVCpusIsValid(vCpuCount),
        memoryIsValid(totalMemory, vCpuCount),
        vCpuCountForLocationIsValid(vCpuCount, location),
      ]);
    }

    return {
      generateValidVCpuListForLocation,
      generateValidMemoryListForVCpuCount,
      generateInstanceTypeString,
      customInstanceChoicesAreValid,
      memoryIsValid,
      vCpuCountForLocationIsValid,
      parseInstanceTypeString,
    };
  });
