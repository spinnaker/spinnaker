'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.projects.write.service', [
    require('../../task/taskExecutor.js'),
    require('../../utils/lodash.js'),
  ])
  .factory('projectWriter', function($q, taskExecutor) {

    function upsertProject(project) {
      let descriptor = project.id ? 'Update' : 'Create';
      return taskExecutor.executeTask({
        job: [
          {
            type: 'upsertProject',
            project: project
          }
        ],
        description: descriptor + ' project: ' + project.name
      });
    }

    function deleteProject(project) {
      return taskExecutor.executeTask({
        job: [
          {
            type: 'deleteProject',
            project: {
              id: project.id,
            },
          }
        ],
        description: 'Delete project: ' + project.name
      });
    }

    return {
      upsertProject: upsertProject,
      deleteProject: deleteProject,
    };

  });
