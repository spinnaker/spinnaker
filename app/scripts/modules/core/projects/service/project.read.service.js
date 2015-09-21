'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.projects.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
  ])
  .factory('projectReader', function ($q, Restangular) {

    function listProjects() {
      if (!arguments.length) {
        return Restangular.all('projects').getList();
      }
      return $q.when([{ name: 'API' }]);
    }

    function getProjectConfig(projectName) {
      if (arguments.length === 1) {
        return listProjects().then((projects) => {
          let project = projects.find((test) => test.name.toLowerCase() === projectName.toLowerCase());
          if (project) {
            return Restangular.one('projects', project.id).get();
          } else {
            return $q.reject(projectName);
          }
        });
      }
    }

    return {
      listProjects: listProjects,
      getProjectConfig: getProjectConfig,
    };

  }).name;
