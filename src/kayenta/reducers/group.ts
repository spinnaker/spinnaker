import { Action, combineReducers } from 'redux';
import { get } from 'lodash';

import * as Actions from '../actions';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';

export interface IGroupState {
  list: string[];
  selected: string;
  groupWeights: {[group: string]: number};
}

function list(state: string[] = [], action: Action & any): string[] {
  function groupsFromMetrics(metrics: ICanaryMetricConfig[] = []) {
    return metrics.reduce((groups, metric) => {
      return groups.concat(metric.groups.filter((group: string) => !groups.includes(group)))
    }, []).sort();
  }

  switch (action.type) {
    case Actions.INITIALIZE:
      return groupsFromMetrics(action.state.selectedConfig.metricList);

    case Actions.SELECT_CONFIG:
      return groupsFromMetrics(action.config.metrics);

    case Actions.ADD_METRIC:
      const groups = action.metric.groups;
      return state.concat(groups.filter((group: string) => !state.includes(group)));

    case Actions.ADD_GROUP:
      let n = 1;
      let name = null;
      do {
        name = 'Group ' + n;
        n++;
      } while (state.includes(name));
      return state.concat([name]);

    default:
      return state;
  }
}

function selected(state: string = null, action: Action & any): string {
  switch (action.type) {
    case Actions.INITIALIZE:
      return action.state.selectedConfig.group.selected;

    case Actions.SELECT_GROUP:
      return action.name;

    default:
      return state;
  }
}

function groupWeights(state: {[group: string]: number} = {}, action: Action & any): {[group: string]: number} {
  switch (action.type) {
    case Actions.INITIALIZE:
      return action.state.selectedConfig.group.groupWeights;

    case Actions.SELECT_CONFIG:
      return get(action, 'config.classifier.groupWeights', {});

    case Actions.UPDATE_GROUP_WEIGHT:
      return { ...state, [action.group]: action.weight };

    default:
      return state;
  }
}

export const group = combineReducers<IGroupState>({
  list,
  selected,
  groupWeights,
});
