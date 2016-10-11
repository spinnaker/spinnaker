import { module } from 'angular';

import { TaskStep } from '../domain/taskStep';

export function displayableTaskFilter() {
  let blacklist = [
    'stageStart', 'stageEnd'
  ];
  return function (input: TaskStep[]): TaskStep[] {
    if (input) {
      return input.filter((test: TaskStep) => {
        return !blacklist.includes(test.name);
      });
    }
  };
}

const MODULE_NAME = 'spinnaker.pipelines.stages.core.displayableTasks.filter';

module(MODULE_NAME, [])
  .filter('displayableTasks', displayableTaskFilter);

export default MODULE_NAME;
