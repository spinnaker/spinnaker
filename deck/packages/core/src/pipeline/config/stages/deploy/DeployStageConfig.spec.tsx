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
      stageFieldUpdated: vi.fn(),
      updateStage: vi.fn(),
      updateStageField: vi.fn(),
    };
  }

  it('shows provider selection errors when adding a cluster', async () => {
    vi.spyOn(AccountService, 'listProviders').mockReturnValue(Promise.resolve(['aws']) as any);
    vi.spyOn(ProviderSelectionService, 'selectProvider').mockReturnValue(
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
    vi.spyOn(AccountService, 'listProviders').mockReturnValue(Promise.resolve(['aws']) as any);
    let filterFn: any;
    vi.spyOn(ProviderSelectionService, 'selectProvider').mockImplementation(
      (_application, _feature, providerFilter) => {
        filterFn = providerFilter;
        return Promise.reject(new Error('cancelled')) as any;
      },
    );
    const props = createProps();
    const component = mount(<DeployStageConfigComponent {...props} deckRuntimeServices={deckRuntimeServices} />);

    component.find('button.add-new').simulate('click');

    expect(ProviderSelectionService.selectProvider).toHaveBeenCalledWith(
      props.application,
      'serverGroup',
      expect.any(Function),
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
