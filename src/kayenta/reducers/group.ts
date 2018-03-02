import { Action, combineReducers, Reducer } from 'redux';
import { combineActions, handleActions } from 'redux-actions';
import { get } from 'lodash';

import * as Actions from '../actions';
import { IGroupWeights, ICanaryMetricConfig } from '../domain/ICanaryConfig';

export interface IGroupState {
  list: string[];
  selected: string;
  groupWeights: {[group: string]: number};
  edit: string;
}

function groupsFromMetrics(metrics: ICanaryMetricConfig[] = []) {
  const results = metrics.reduce((groups, metric) => {
    return groups.concat(metric.groups.filter((g: string) => !groups.includes(g)))
  }, []).sort();
  if (!results.length) {
    results.push('Group 1');
  }
  return results;
}

const list = handleActions({
  [Actions.SELECT_CONFIG]: (_state: string[], action: Action & any) => groupsFromMetrics(action.payload.config.metrics),
  [Actions.ADD_METRIC]: (state: string[], action: Action & any) => {
    const groups = action.payload.metric.groups;
    return state.concat(groups.filter((g: string) => !state.includes(g)));
  },
  [Actions.ADD_GROUP]: (state: string[]) => {
    let n = 1;
    let name = null;
    do {
      name = 'Group ' + n;
      n++;
    } while (state.includes(name));
    return state.concat([name]);
  },
}, []);

const selected = handleActions({
  [Actions.SELECT_GROUP]: (_state: string, action: Action & any) => action.payload.name,
  [Actions.SELECT_CONFIG]: () => '',
}, '');

const groupWeights = handleActions({
  [Actions.SELECT_CONFIG]: (_state: IGroupWeights, action: Action & any) => get(action, 'payload.config.classifier.groupWeights', {}),
  [Actions.UPDATE_GROUP_WEIGHT]: (state: IGroupWeights, action: Action & any) => ({ ...state, [action.payload.group]: action.payload.weight }),
}, {});

const edit = handleActions({
  [Actions.EDIT_GROUP_BEGIN]: (_state: string, action: Action & any) => action.payload.group,
  [Actions.EDIT_GROUP_UPDATE]: (_state: string, action: Action & any) => action.payload.edit,
  [combineActions(Actions.SELECT_GROUP, Actions.EDIT_GROUP_CONFIRM)]: () => null,
}, null);

export const group: Reducer<IGroupState> = combineReducers<IGroupState>({
  list,
  selected,
  groupWeights,
  edit,
});
