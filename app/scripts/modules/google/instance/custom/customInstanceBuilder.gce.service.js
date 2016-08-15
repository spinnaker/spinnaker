'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.customInstanceBuilder.gce.service', [
  require('../../../core/utils/lodash.js'),
  require('../gceVCpuMaxByLocation.value.js'),
])
  .factory('gceCustomInstanceBuilderService', function(gceVCpuMaxByLocation, _) {
    const defaultMax = 16;

    function vCpuCountForLocationIsValid(vCpuCount, location) {
      let knownLocation = location in gceVCpuMaxByLocation;
      return _.some([
        !knownLocation && vCpuCount <= defaultMax,
        knownLocation && vCpuCount <= gceVCpuMaxByLocation[location]]);
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
      let max = gceVCpuMaxByLocation[location] || defaultMax;
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
