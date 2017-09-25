import { Action, combineReducers } from 'redux';
import { combineActions, handleActions } from 'redux-actions';
import { get } from 'lodash';

import * as Actions from '../actions';
import { GroupWeights, ICanaryMetricConfig } from '../domain/ICanaryConfig';

export interface IGroupState {
  list: string[];
  selected: string;
  groupWeights: {[group: string]: number};
  edit: string;
}

function groupsFromMetrics(metrics: ICanaryMetricConfig[] = []) {
  return metrics.reduce((groups, metric) => {
    return groups.concat(metric.groups.filter((group: string) => !groups.includes(group)))
  }, []).sort();
}

const list = handleActions({
  [Actions.SELECT_CONFIG]: (_state: string[], action: Action & any) => groupsFromMetrics(action.payload.config.metrics),
  [Actions.ADD_METRIC]: (state: string[], action: Action & any) => {
    const groups = action.payload.metric.groups;
    return state.concat(groups.filter((group: string) => !state.includes(group)));
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
}, '');

const groupWeights = handleActions({
  [Actions.SELECT_CONFIG]: (_state: GroupWeights, action: Action & any) => get(action, 'payload.config.classifier.groupWeights', {}),
  [Actions.UPDATE_GROUP_WEIGHT]: (state: GroupWeights, action: Action & any) => ({ ...state, [action.payload.group]: action.payload.weight }),
}, {});

const edit = handleActions({
  [Actions.EDIT_GROUP_BEGIN]: (_state: string, action: Action & any) => action.payload.group,
  [Actions.EDIT_GROUP_UPDATE]: (_state: string, action: Action & any) => action.payload.edit,
  [combineActions(Actions.SELECT_GROUP, Actions.EDIT_GROUP_CONFIRM)]: () => null,
}, null);

export const group = combineReducers<IGroupState>({
  list,
  selected,
  groupWeights,
  edit,
});
