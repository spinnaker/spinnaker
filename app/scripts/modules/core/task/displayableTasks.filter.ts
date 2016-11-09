import { module } from 'angular';

import { TaskStep } from '../domain/taskStep';

export function displayableTaskFilter() {
  let blacklist = [
    'stageStart', 'stageEnd', 'determineTargetServerGroup'
  ];
  return function (input: TaskStep[]): TaskStep[] {
    if (input) {
      return input.filter((test: TaskStep) => {
        return !blacklist.includes(test.name);
      });
    }
  };
}

export const DISPLAYABLE_TASKS_FILTER = 'spinnaker.pipelines.stages.core.displayableTasks.filter';
module(DISPLAYABLE_TASKS_FILTER, [])
  .filter('displayableTasks', displayableTaskFilter);
