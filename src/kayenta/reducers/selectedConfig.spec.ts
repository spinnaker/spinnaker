import * as Actions from 'kayenta/actions/index';
import {
  changeMetricGroupConfirmReducer,
  editGroupConfirmReducer,
  ISelectedConfigState, updateGroupWeightsReducer
} from './selectedConfig';
import { IGroupWeights, ICanaryMetricConfig } from '../domain/ICanaryConfig';
import { IGroupState } from './group';

describe('Reducer: editGroupConfirmReducer', () => {
  let state: ISelectedConfigState;

  beforeEach(() => {
    state = createSelectedConfigState('groupA');
  });

  it('ignores actions other than EDIT_GROUP_CONFIRM', () => {
    expect(editGroupConfirmReducer(state, { type: 'arbitrary_action' })).toEqual(createSelectedConfigState('groupA'));
  });

  it('replaces a group name throughout the app state', () => {
    const action = {
      type: Actions.EDIT_GROUP_CONFIRM,
      payload: {
        group: 'groupA',
        edit: 'groupB',
      },
    };

    expect(editGroupConfirmReducer(state, action)).toEqual(createSelectedConfigState('groupB'));
  });

  it('ignores the updated group name if the value is JS false', () => {
    const editedGroupNames = ['', null, undefined];

    editedGroupNames.forEach(edited => {
      const action = {
        type: Actions.EDIT_GROUP_CONFIRM,
        payload: {
          group: 'groupA',
          edit: edited,
        },
      };

      expect(editGroupConfirmReducer(state, action)).toEqual(createSelectedConfigState('groupA'));
    });
  });

  it('ignores the updated group name if the group already exists', () => {
    const action = {
      type: Actions.EDIT_GROUP_CONFIRM,
      payload: {
        group: 'groupA',
        edit: 'groupC',
      },
    };

    expect(editGroupConfirmReducer(state, action)).toEqual(createSelectedConfigState('groupA'));
  });

  const createSelectedConfigState = (groupName: string): ISelectedConfigState => ({
    metricList: [{
      name: 'metricA',
      groups: [groupName, 'groupC'],
    }] as ICanaryMetricConfig[],
    group: {
      list: [groupName, 'groupC'],
      groupWeights: {
        [groupName]: 50,
        otherGroupName: 50,
      },
      selected: groupName,
    }
  } as any); // Ignore missing fields for type ISelectedConfigState.
});

describe('Reducer: changeMetricGroupConfirmReducer', () => {

  it('updates a metric\'s group, leaving other metrics\'s groups unchanged', () => {
    const state = createSelectedConfigState(['myGroup'], 'updatedGroup');
    const action = createAction('1');

    const updatedState = changeMetricGroupConfirmReducer(state, action);

    expect(
      updatedState.metricList.find(m => m.id === '1').groups
    ).toEqual(['updatedGroup']);

    expect(
      updatedState.metricList.find(m => m.id === '2').groups
    ).toEqual([]);
  });

  it('handles metrics with multiple groups', () => {
    let state = createSelectedConfigState(['a', 'b'], 'c');
    let action = createAction('1');

    let updatedState = changeMetricGroupConfirmReducer(state, action);

    expect(
      updatedState.metricList.find(m => m.id === '1').groups
    ).toEqual(['c']);

    state = createSelectedConfigState(['a', 'b'], 'b');
    action = createAction('1');

    updatedState = changeMetricGroupConfirmReducer(state, action);

    expect(
      updatedState.metricList.find(m => m.id === '1').groups
    ).toEqual(['b']);
  });

  const createAction = (metricId: string) => ({
    type: Actions.CHANGE_METRIC_GROUP_CONFIRM,
    payload: {
      metricId,
    },
  });

  const createSelectedConfigState = (groups: string[], toGroup: string): ISelectedConfigState => ({
    metricList: [
      {
        name: 'myMetric',
        id: '1',
        groups,
      },
      {
        name: 'myOtherMetric',
        id: '2',
        groups: [],
      }
    ] as ICanaryMetricConfig[],
    changeMetricGroup: {
      toGroup,
    },
  } as any);
});

describe('Reducer: updateGroupWeightsReducer', () => {

  it('prunes weights for groups that do not exist', () => {
    const metrics = [
      {
        name: 'metricA',
        groups: ['a', 'b']
      },
      {
        name: 'metricB',
        groups: ['c'],
      }
    ] as ICanaryMetricConfig[];
    const weights: IGroupWeights = { a: 25, b: 25, c: 25, d: 25 };

    const state = createSelectedConfigState(metrics, weights);
    const action = createAction();

    const updatedState = updateGroupWeightsReducer(state, action);
    expect(updatedState.group.groupWeights).toEqual({ a: 25, b: 25, c: 25 });
  });

  it('initializes weights for groups that exist on the metrics only', () => {
    const metrics = [
      {
        name: 'metricA',
        groups: ['a']
      },
      {
        name: 'metricB',
        groups: ['b', 'c'],
      }
    ] as ICanaryMetricConfig[];
    const weights: IGroupWeights = { a: 50, b: 50 };

    const state = createSelectedConfigState(metrics, weights);
    const action = createAction();

    const updatedState = updateGroupWeightsReducer(state, action);
    expect(updatedState.group.groupWeights).toEqual({ a: 50, b: 50, c: 0 });
  });

  const createAction = () => ({ type: Actions.SELECT_CONFIG });

  const createSelectedConfigState = (metricList: ICanaryMetricConfig[], groupWeights: IGroupWeights): ISelectedConfigState => ({
    metricList,
    group: {
      groupWeights,
    } as IGroupState,
  } as any);
});
