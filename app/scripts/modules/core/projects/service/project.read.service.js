'use strict';

let angular = require('angular');

import {SETTINGS} from 'core/config/settings';

module.exports = angular
  .module('spinnaker.core.projects.read.service', [
  ])
  .factory('projectReader', function ($http) {

    function listProjects() {
      let url = [SETTINGS.gateUrl, 'projects'].join('/');
      return $http({
        method: 'GET',
        url: url,
        timeout: SETTINGS.pollSchedule * 2 + 5000, // TODO: replace with apiHost call
      }).then((resp) => resp.data);
    }

    function getProjectConfig(projectName) {
      let url = [SETTINGS.gateUrl, 'projects', projectName].join('/');
      return $http({
        method: 'GET',
        url: url,
        timeout: SETTINGS.pollSchedule * 2 + 5000, // TODO: replace with apiHost call
      }).then((resp) => resp.data);
    }

    function getProjectClusters(projectName) {
      let url = [SETTINGS.gateUrl, 'projects', projectName, 'clusters'].join('/');
      return $http({
        method: 'GET',
        url: url,
        timeout: SETTINGS.pollSchedule * 2 + 5000, // TODO: replace with apiHost call
      }).then((resp) => resp.data);
    }

    return {
      listProjects: listProjects,
      getProjectConfig: getProjectConfig,
      getProjectClusters: getProjectClusters,
    };

  });
