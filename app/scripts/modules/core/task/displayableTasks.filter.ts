import { module } from 'angular';

import { TaskStep } from '../domain/taskStep';

export function displayableTaskFilter() {
  let blacklist = [
    'stageStart', 'stageEnd'
  ];
  return function (input: TaskStep[]) {
    if (input) {
      return input.filter(function (test: TaskStep) {
        return blacklist.indexOf(test.name) === -1 ? input : null;
      });
    }
  };
}

const MODULE_NAME = 'spinnaker.pipelines.stages.core.displayableTasks.filter';

module(MODULE_NAME, [])
  .filter('displayableTasks', displayableTaskFilter);

export default MODULE_NAME;
