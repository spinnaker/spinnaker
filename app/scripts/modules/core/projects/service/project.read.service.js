'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.projects.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../../utils/lodash.js'),
  ])
  .factory('projectReader', function ($q, Restangular, _) {

    function listProjects() {
      return Restangular.all('projects').getList();
    }

    function getProjectConfig(projectName) {
      return listProjects().then((projects) => {
        let project = _.find(projects, (test) => test.name.toLowerCase() === projectName.toLowerCase());
        if (project) {
          return Restangular.one('projects', project.id).get();
        } else {
          return $q.reject(projectName);
        }
      });
    }

    return {
      listProjects: listProjects,
      getProjectConfig: getProjectConfig,
    };

  });
