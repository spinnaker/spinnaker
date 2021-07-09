import { module } from 'angular';

import { ITaskStep } from '../domain';

export function displayableTasks(input: ITaskStep[]): ITaskStep[] {
  const denylist = ['stageStart', 'stageEnd', 'determineTargetServerGroup'];

  let result: ITaskStep[] = [];
  if (input) {
    result = input.filter((test: ITaskStep) => !denylist.includes(test.name) || test.status === 'TERMINAL');
  }

  return result;
}
export function displayableTaskFilter() {
  return displayableTasks;
}

export const DISPLAYABLE_TASKS_FILTER = 'spinnaker.pipelines.stages.common.displayableTasks.filter';
module(DISPLAYABLE_TASKS_FILTER, []).filter('displayableTasks', displayableTaskFilter);
