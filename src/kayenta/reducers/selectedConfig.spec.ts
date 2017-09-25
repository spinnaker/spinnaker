import * as Actions from 'kayenta/actions/index';
import { editGroupConfirm, ISelectedConfigState } from './selectedConfig';
import { ICanaryMetricConfig } from '../domain/ICanaryConfig';

describe('Reducer: editGroupConfirm', () => {
  let state: ISelectedConfigState;

  beforeEach(() => {
    state = createSelectedConfigState('groupA');
  });

  it('ignores actions other than EDIT_GROUP_CONFIRM', () => {
    expect(editGroupConfirm(state, { type: 'arbitrary_action' })).toEqual(createSelectedConfigState('groupA'));
  });

  it('replaces a group name throughout the app state', () => {
    const action = {
      type: Actions.EDIT_GROUP_CONFIRM,
      payload: {
        group: 'groupA',
        edit: 'groupB',
      },
    };

    expect(editGroupConfirm(state, action)).toEqual(createSelectedConfigState('groupB'));
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

      expect(editGroupConfirm(state, action)).toEqual(createSelectedConfigState('groupA'));
    });
  });
});

const createSelectedConfigState = (groupName: string): ISelectedConfigState => ({
  metricList: [{
    name: 'metricA',
    groups: [groupName, 'otherGroupName'],
  }] as ICanaryMetricConfig[],
  group: {
    list: [groupName, 'otherGroupName'],
    groupWeights: {
      [groupName]: 50,
      otherGroupName: 50,
    },
    selected: groupName,
  }
} as any); // Ignore missing fields for type ISelectedConfigState.

