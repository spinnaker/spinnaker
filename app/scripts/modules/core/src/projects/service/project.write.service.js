'use strict';

import { TaskExecutor } from 'core/task/taskExecutor';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.projects.write.service', []).factory('projectWriter', function() {
  function upsertProject(project) {
    let descriptor = project.id ? 'Update' : 'Create';
    return TaskExecutor.executeTask({
      job: [
        {
          type: 'upsertProject',
          project: project,
        },
      ],
      project: project,
      description: descriptor + ' project: ' + project.name,
    });
  }

  function deleteProject(project) {
    return TaskExecutor.executeTask({
      job: [
        {
          type: 'deleteProject',
          project: {
            id: project.id,
          },
        },
      ],
      project: project,
      description: 'Delete project: ' + project.name,
    });
  }

  return {
    upsertProject: upsertProject,
    deleteProject: deleteProject,
  };
});
