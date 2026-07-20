import { mount } from 'enzyme';
import React from 'react';

import { SETTINGS } from '../../../../config/settings';
import { NotificationsList } from '../../../../notification';
import { ManualJudgmentStageConfig } from './ManualJudgmentStageConfig';

describe('<ManualJudgmentStageConfig />', () => {
  const createProps = (stageOverrides = {}) => {
    const stage: any = {
      name: 'Manual Judgment',
      refId: '1',
      requisiteStageRefIds: [],
      type: 'manualJudgment',
      ...stageOverrides,
    };

    return {
      application: {} as any,
      pipeline: { stages: [stage] } as any,
      stage,
      stageFieldUpdated: jasmine.createSpy('stageFieldUpdated'),
      updateStage: jasmine.createSpy('updateStage'),
      updateStageField: jasmine
        .createSpy('updateStageField')
        .and.callFake((changes: any) => Object.assign(stage, changes)),
    };
  };

  afterEach(() => SETTINGS.resetToOriginal());

  it('sets legacy defaults without marking the stage dirty', () => {
    const props = createProps();

    mount(<ManualJudgmentStageConfig {...props} />);

    expect(props.stage.notifications).toEqual([]);
    expect(props.stage.judgmentInputs).toEqual([]);
    expect(props.stage.failPipeline).toBe(true);
    expect(props.updateStageField).not.toHaveBeenCalled();
    expect(props.stageFieldUpdated).not.toHaveBeenCalled();
  });

  it('preserves an existing false failPipeline default', () => {
    const props = createProps({ failPipeline: false });

    mount(<ManualJudgmentStageConfig {...props} />);

    expect(props.stage.failPipeline).toBe(false);
  });

  it('updates instructions through updateStageField', () => {
    const props = createProps();
    const component = mount(<ManualJudgmentStageConfig {...props} />);

    component.find('textarea').simulate('change', { target: { value: 'Approve this deploy' } });

    expect(props.updateStageField).toHaveBeenCalledWith({ instructions: 'Approve this deploy' });
  });

  it('renders auth controls only when auth is enabled and updates their fields', () => {
    SETTINGS.authEnabled = false;
    let component = mount(<ManualJudgmentStageConfig {...createProps()} />);
    expect(component.find('input[type="checkbox"]').length).toBe(1);

    SETTINGS.authEnabled = true;
    const props = createProps();
    component = mount(<ManualJudgmentStageConfig {...props} />);
    const checkboxes = component.find('input[type="checkbox"]');

    checkboxes.at(0).simulate('change', { target: { checked: true } });
    checkboxes.at(1).simulate('change', { target: { checked: true } });

    expect(props.updateStageField).toHaveBeenCalledWith({ propagateAuthenticationContext: true });
    expect(props.updateStageField).toHaveBeenCalledWith({ preventSelfApproval: true });
  });

  it('adds, edits, and removes judgment inputs', () => {
    const props = createProps({ judgmentInputs: [{ value: 'yes' }] });
    const component = mount(<ManualJudgmentStageConfig {...props} />);

    component.find('button.add-new').simulate('click');
    expect(props.updateStageField).toHaveBeenCalledWith({ judgmentInputs: [{ value: 'yes' }, {}] });

    component.setProps({ stage: props.stage });
    component
      .find('input[type="text"]')
      .at(1)
      .simulate('change', { target: { value: 'later' } });
    expect(props.updateStageField).toHaveBeenCalledWith({ judgmentInputs: [{ value: 'yes' }, { value: 'later' }] });

    component.setProps({ stage: props.stage });
    component
      .find('a')
      .filterWhere((link) => link.text() === 'Remove')
      .at(0)
      .simulate('click');
    expect(props.updateStageField).toHaveBeenCalledWith({ judgmentInputs: [{ value: 'later' }] });
  });

  it('clears notifications when send notifications is toggled off', () => {
    const props = createProps({ sendNotifications: true, notifications: [{ type: 'email' }] });
    const component = mount(<ManualJudgmentStageConfig {...props} />);

    component
      .find('input[type="checkbox"]')
      .last()
      .simulate('change', { target: { checked: false } });

    expect(props.updateStageField).toHaveBeenCalledWith({ sendNotifications: undefined, notifications: [] });
  });

  it('renders stage Manual Judgment notifications and updates them through updateStageField', () => {
    const props = createProps({ sendNotifications: true, notifications: [{ type: 'email' }] });
    const component = mount(<ManualJudgmentStageConfig {...props} />);
    const notifications = component.find(NotificationsList);
    const updatedNotifications = [{ type: 'slack' }] as any;

    expect(notifications.prop('level')).toBe('stage');
    expect(notifications.prop('stageType')).toBe('manualJudgment');
    expect(notifications.prop('notifications')).toBe(props.stage.notifications);

    notifications.prop('updateNotifications')(updatedNotifications);

    expect(props.updateStageField).toHaveBeenCalledWith({ notifications: updatedNotifications });
  });
});
