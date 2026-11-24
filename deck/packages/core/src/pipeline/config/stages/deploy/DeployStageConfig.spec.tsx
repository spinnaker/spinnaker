import { render, screen } from '@testing-library/react';
import { setupUser } from '../../../../utils/testUtils/userEvent';
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
    const user = setupUser();
    vi.spyOn(AccountService, 'listProviders').mockReturnValue(Promise.resolve(['aws']) as any);
    vi.spyOn(ProviderSelectionService, 'selectProvider').mockImplementation(() =>
      Promise.reject(new Error('No providers support serverGroup for this action.')),
    );
    render(<DeployStageConfigComponent {...createProps()} deckRuntimeServices={deckRuntimeServices} />);

    await user.click(screen.getByRole('button', { name: /Add server group/i }));

    expect(await screen.findByText('No providers support serverGroup for this action.')).toBeVisible();
  });

  it('only offers providers with React clone server group modals when adding a cluster', async () => {
    const user = setupUser();
    vi.spyOn(AccountService, 'listProviders').mockReturnValue(Promise.resolve(['aws']) as any);
    let filterFn: any;
    vi.spyOn(ProviderSelectionService, 'selectProvider').mockImplementation(
      (_application, _feature, providerFilter) => {
        filterFn = providerFilter;
        return Promise.reject(new Error('cancelled')) as any;
      },
    );
    const props = createProps();
    render(<DeployStageConfigComponent {...props} deckRuntimeServices={deckRuntimeServices} />);

    await user.click(screen.getByRole('button', { name: /Add server group/i }));

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
