'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.projects.read.service', [
  ])
  .factory('projectReader', function ($http, settings) {

    function listProjects() {
      let url = [settings.gateUrl, 'projects'].join('/');
      return $http({
        method: 'GET',
        url: url,
        timeout: settings.pollSchedule * 2 + 5000, // TODO: replace with apiHost call
      }).then((resp) => resp.data);
    }

    function getProjectConfig(projectName) {
      let url = [settings.gateUrl, 'projects', projectName].join('/');
      return $http({
        method: 'GET',
        url: url,
        timeout: settings.pollSchedule * 2 + 5000, // TODO: replace with apiHost call
      }).then((resp) => resp.data);
    }

    function getProjectClusters(projectName) {
      let url = [settings.gateUrl, 'projects', projectName, 'clusters'].join('/');
      return $http({
        method: 'GET',
        url: url,
        timeout: settings.pollSchedule * 2 + 5000, // TODO: replace with apiHost call
      }).then((resp) => resp.data);
    }

    return {
      listProjects: listProjects,
      getProjectConfig: getProjectConfig,
      getProjectClusters: getProjectClusters,
    };

  });
