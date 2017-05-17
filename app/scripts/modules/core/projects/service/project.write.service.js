'use strict';

import {TASK_EXECUTOR} from 'core/task/taskExecutor';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.projects.write.service', [
    TASK_EXECUTOR,
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
        project: project,
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
        project: project,
        description: 'Delete project: ' + project.name
      });
    }

    return {
      upsertProject: upsertProject,
      deleteProject: deleteProject,
    };
  });
