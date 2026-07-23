import { mount } from 'enzyme';
import React from 'react';

import { AccountService } from '../../../../account/AccountService';
import { ProviderSelectionService } from '../../../../cloudProvider/providerSelection/ProviderSelectionService';
import { DeployStageConfigComponent } from './DeployStageConfig';

describe('<DeployStageConfig />', () => {
  const deckRuntimeServices = { serverGroupCommandBuilder: {}, serverGroupTransformer: {} } as any;

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
    const component = mount(
      <DeployStageConfigComponent {...createProps()} deckRuntimeServices={deckRuntimeServices} />,
    );

    component.find('button.add-new').simulate('click');
    await Promise.resolve();
    await Promise.resolve();
    component.update();

    expect(component.find('.alert-danger').text()).toBe('No providers support serverGroup for this action.');
  });

  it('only offers providers with React clone server group modals when adding a cluster', () => {
    spyOn(AccountService, 'listProviders').and.returnValue(Promise.resolve(['aws']) as any);
    let filterFn: any;
    spyOn(ProviderSelectionService, 'selectProvider').and.callFake((_application, _feature, providerFilter) => {
      filterFn = providerFilter;
      return Promise.reject(new Error('cancelled')) as any;
    });
    const props = createProps();
    const component = mount(<DeployStageConfigComponent {...props} deckRuntimeServices={deckRuntimeServices} />);

    component.find('button.add-new').simulate('click');

    expect(ProviderSelectionService.selectProvider).toHaveBeenCalledWith(
      props.application,
      'serverGroup',
      jasmine.any(Function),
    );
    expect(filterFn(props.application, {}, { serverGroup: { CloneServerGroupModal: { show: () => null } } })).toBe(
      true,
    );
    expect(
      filterFn(
        props.application,
        {},
        { serverGroup: { CloneServerGroupModal: { show: () => null } }, unsupportedStageTypes: ['deploy'] },
      ),
    ).toBe(false);
    expect(filterFn(props.application, {}, { serverGroup: {} })).toBe(false);
  });
});
