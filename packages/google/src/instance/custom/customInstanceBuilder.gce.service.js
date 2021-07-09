'use strict';

import { module } from 'angular';
import _ from 'lodash';

export const GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCEBUILDER_GCE_SERVICE =
  'spinnaker.serverGroup.customInstanceBuilder.gce.service';
export const name = GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCEBUILDER_GCE_SERVICE; // for backwards compatibility
module(GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCEBUILDER_GCE_SERVICE, []).factory(
  'gceCustomInstanceBuilderService',
  function () {
    function vCpuCountForLocationIsValid(instanceFamily, vCpuCount, location, locationToInstanceTypesMap) {
      let max = 0;
      switch (instanceFamily) {
        case 'E2':
          // E2 support up to 32 cores
          // https://cloud.google.com/compute/docs/instances/creating-instance-with-custom-machine-type#e2_custom_machine_types
          max = 32;
          break;
        case 'N2':
        case 'N2D':
        case 'N1':
        default:
          max = locationToInstanceTypesMap[location].vCpuMax;
      }
      return vCpuCount <= max;
    }

    /*
     * Above 1, vCPU count must be even.
     * */
    function numberOfVCpusIsValid(instanceFamily, vCpuCount) {
      if (vCpuCount === 1) {
        return true;
      }
      switch (instanceFamily) {
        case 'N2':
        case 'N2D':
          return vCpuCount % 4 === 0;
        case 'E2':
        case 'N1':
        default:
          return vCpuCount % 2 === 0;
      }
    }

    function generateValidVCpuListForLocation(location, locationToInstanceTypesMap) {
      const max = locationToInstanceTypesMap[location].vCpuMax;
      return [1, ..._.range(2, max, 2), max];
    }

    function generateValidInstanceFamilyList() {
      return ['n1', 'e2', 'n2', 'n2d'].map((x) => x.toUpperCase());
    }

    /*
     * Memory per vCPU must be between .9 GB and 6.5 GB
     * Total memory must be a multiple of 256 MB.
     * */
    function minMemoryForVCpuCount(instanceFamily, vCpuCount) {
      switch (instanceFamily) {
        case 'E2':
        case 'N2':
        case 'N2D':
          return Math.ceil(0.5 * vCpuCount * 4) / 4;
        case 'N1':
        default:
          return Math.ceil(0.9 * vCpuCount * 4) / 4;
      }
    }

    function maxMemoryForVCpuCount(instanceFamily, vCpuCount) {
      switch (instanceFamily) {
        case 'E2':
          return 8 * vCpuCount > 128 ? 128 : 8 * vCpuCount;
        case 'N2':
        case 'N2D':
          return 8 * vCpuCount;
        case 'N1':
        default:
          return 6.5 * vCpuCount;
      }
    }

    function generateValidMemoryListForVCpuCount(instanceFamily, vCpuCount) {
      const min = minMemoryForVCpuCount(instanceFamily, vCpuCount);
      const max = maxMemoryForVCpuCount(instanceFamily, vCpuCount);
      return [..._.range(min, max, 0.25), max];
    }

    function memoryIsValid(instanceFamily, totalMemory, vCpuCount) {
      const min = minMemoryForVCpuCount(instanceFamily, vCpuCount);
      const max = maxMemoryForVCpuCount(instanceFamily, vCpuCount);
      return _.inRange(totalMemory, min, max) || totalMemory === max;
    }

    /*
     * In the API, you must always provide memory in MB units.
     * Format: custom-NUMBER_OF_CPUS-AMOUNT_OF_MEMORY
     * */
    function generateInstanceTypeString(instanceFamily, vCpuCount, totalMemory) {
      const memoryInMbs = Number(totalMemory) * 1024;
      instanceFamily = instanceFamily.toLowerCase();
      if (instanceFamily === 'n1') {
        return `custom-${vCpuCount}-${memoryInMbs}`;
      }
      return `${instanceFamily}-custom-${vCpuCount}-${memoryInMbs}`;
    }

    function parseInstanceTypeString(instanceTypeString) {
      const [vCpuCount, memoryInMbs] = _.chain(instanceTypeString.split('-'))
        .takeRight(2)
        .map((value) => Number(value))
        .value();

      let instanceFamily = instanceTypeString.split('-')[0].toUpperCase();
      if (instanceFamily === 'CUSTOM') {
        instanceFamily = 'N1';
      }

      const memory = memoryInMbs / 1024;

      return { instanceFamily, vCpuCount, memory };
    }

    function customInstanceChoicesAreValid(
      instanceFamily,
      vCpuCount,
      totalMemory,
      location,
      locationToInstanceTypesMap,
    ) {
      return _.every([
        numberOfVCpusIsValid(instanceFamily, vCpuCount),
        memoryIsValid(instanceFamily, totalMemory, vCpuCount),
        vCpuCountForLocationIsValid(instanceFamily, vCpuCount, location, locationToInstanceTypesMap),
      ]);
    }

    return {
      generateValidVCpuListForLocation,
      generateValidMemoryListForVCpuCount,
      generateValidInstanceFamilyList,
      generateInstanceTypeString,
      customInstanceChoicesAreValid,
      memoryIsValid,
      vCpuCountForLocationIsValid,
      parseInstanceTypeString,
    };
  },
);
