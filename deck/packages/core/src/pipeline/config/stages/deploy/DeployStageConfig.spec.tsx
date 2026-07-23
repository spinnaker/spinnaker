import { mount } from 'enzyme';
import React from 'react';

import { AccountService } from '../../../../account/AccountService';
import { ProviderSelectionService } from '../../../../cloudProvider/providerSelection/ProviderSelectionService';
import { DeployStageConfig } from './DeployStageConfig';

describe('<DeployStageConfig />', () => {
  function createProps(stageOverrides = {}) {
    const stage = {
      clusters: [],
      refId: '1',
      requisiteStageRefIds: [],
      type: 'deploy',
      ...stageOverrides,
    };

    return {
      application: { name: 'fnord' } as any,
      pipeline: { stages: [stage] } as any,
      stage,
      stageFieldUpdated: jasmine.createSpy('stageFieldUpdated'),
      updateStage: jasmine.createSpy('updateStage'),
      updateStageField: jasmine.createSpy('updateStageField'),
    };
  }

  it('shows provider selection errors when adding a cluster', async () => {
    spyOn(AccountService, 'listProviders').and.returnValue(Promise.resolve(['aws']) as any);
    spyOn(ProviderSelectionService, 'selectProvider').and.returnValue(
      Promise.reject(new Error('No providers support serverGroup for this action.')),
    );
    const component = mount(<DeployStageConfig {...createProps()} />);

    component.find('button.add-new').simulate('click');
    await Promise.resolve();
    await Promise.resolve();
    component.update();

    expect(component.find('.alert-danger').text()).toBe('No providers support serverGroup for this action.');
  });
});
