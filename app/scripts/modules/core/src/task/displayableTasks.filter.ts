import { module } from 'angular';

import { ITaskStep } from 'core/domain';

export function displayableTaskFilter() {
  const blacklist = [
    'stageStart', 'stageEnd', 'determineTargetServerGroup'
  ];
  return function (input: ITaskStep[]): ITaskStep[] {
    let result: ITaskStep[] = [];
    if (input) {
      result = input.filter((test: ITaskStep) => {
        return !blacklist.includes(test.name) || test.status === 'TERMINAL';
      });
    }

    return result;
  };
}

export const DISPLAYABLE_TASKS_FILTER = 'spinnaker.pipelines.stages.core.displayableTasks.filter';
module(DISPLAYABLE_TASKS_FILTER, [])
  .filter('displayableTasks', displayableTaskFilter);
