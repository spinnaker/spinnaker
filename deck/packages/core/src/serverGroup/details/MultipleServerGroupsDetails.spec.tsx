import type { Mock } from 'vitest';
import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact, UIViewContext } from '@uirouter/react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { AccountService } from '../../account';
import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import { ProviderSelectionService } from '../../cloudProvider/providerSelection/ProviderSelectionService';
import { ConfirmationModalService } from '../../confirmationModal';
import { ClusterState } from '../../state';
import { MultipleServerGroupsDetails } from './MultipleServerGroupsDetails';

describe('<MultipleServerGroupsDetails />', () => {
  let previousMultiselectModel: any;
  let serverGroupWriter: any;
  let router: UIRouterReact;

  const app = {
    serverGroups: {
      data: [
        {
          account: 'prod',
          instanceCounts: { down: 1, total: 3, up: 2 },
          isDisabled: false,
          name: 'app-v001',
          provider: 'aws',
          region: 'us-west-2',
          type: 'aws',
        },
      ],
      onRefresh: vi.fn().mockReturnValue(() => null),
    },
  } as any;

  const selectedServerGroup = {
    account: 'prod',
    name: 'app-v001',
    provider: 'aws',
    region: 'us-west-2',
    type: 'aws',
  } as any;

  const renderDetails = () =>
    render(
      <UIRouterContext.Provider value={router}>
        <UIViewContext.Provider
          value={{
            fqn: 'application.insight.multipleServerGroups',
            context: router.stateRegistry.get('application.insight.multipleServerGroups') as any,
          }}
        >
          <DeckRuntimeContext.Provider
            value={
              {
                services: {
                  providerServiceDelegate: {
                    getDelegate: () => ({
                      destroyServerGroup: (serverGroup: any) => ({ mixinName: serverGroup.name }),
                    }),
                    hasDelegate: () => true,
                  },
                  serverGroupWriter,
                },
              } as any
            }
          >
            <MultipleServerGroupsDetails app={app} />
          </DeckRuntimeContext.Provider>
        </UIViewContext.Provider>
      </UIRouterContext.Provider>,
    );

  beforeEach(() => {
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    ['application', 'application.insight', 'application.insight.multipleServerGroups'].forEach((name) => {
      router.stateRegistry.register({ name, url: `/${name.split('.').pop()}` } as any);
    });

    previousMultiselectModel = ClusterState.multiselectModel;
    ClusterState.multiselectModel = {
      clearAllServerGroups: () => null,
      serverGroups: [selectedServerGroup],
      serverGroupsStream: { subscribe: () => null },
    } as any;

    serverGroupWriter = {
      destroyServerGroup: vi.fn().mockReturnValue(Promise.resolve({})),
    };

    vi.spyOn(AccountService, 'challengeDestructiveActions').mockReturnValue(Promise.resolve(false));
    vi.spyOn(ProviderSelectionService, 'isDisabled').mockReturnValue(Promise.resolve(false));
    vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    vi.spyOn(ClusterState.multiselectModel.serverGroupsStream, 'subscribe').mockImplementation((callback: any) => {
      callback();
      return { unsubscribe: vi.fn() } as any;
    });
    vi.spyOn(ClusterState.multiselectModel, 'clearAllServerGroups').mockReturnValue(undefined);
  });

  afterEach(() => {
    ClusterState.multiselectModel = previousMultiselectModel;
    router.dispose();
  });

  it('renders selected server group details', () => {
    const { container } = renderDetails();

    expect(screen.getByRole('heading', { name: '1 Server Group' })).toBeInTheDocument();
    expect(screen.getByText('app-v001')).toBeInTheDocument();
    expect(screen.getByText('prod')).toBeInTheDocument();
    expect(screen.getByText(/us-west-2/)).toBeInTheDocument();
    expect(container.querySelectorAll('.multiple-server-group')).toHaveLength(1);
    expect(container.querySelector('multiple-server-group')).not.toBeInTheDocument();
    expect(container.querySelector('.instance-health-counts')).toHaveTextContent('2');
    expect(container.querySelector('.instance-health-counts')).toHaveTextContent('1');
  });

  it('opens destroy confirmation using legacy task monitor semantics', async () => {
    renderDetails();

    await userEvent.click(screen.getByRole('button', { name: 'Actions' }));
    await userEvent.click(screen.getByText('Destroy', { selector: 'a' }));

    expect(ConfirmationModalService.confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        askForReason: true,
        buttonText: 'Destroy 1 server group',
        textToVerify: '1',
      }),
    );
    const confirmation = (ConfirmationModalService.confirm as Mock).mock.lastCall[0];

    confirmation.taskMonitorConfigs[0].submitMethod({ reason: 'user reason' });

    expect(serverGroupWriter.destroyServerGroup).toHaveBeenCalledWith(
      {
        account: 'prod',
        disabled: false,
        instanceCounts: { down: 1, total: 3, up: 2 },
        name: 'app-v001',
        provider: 'aws',
        region: 'us-west-2',
        type: 'aws',
      },
      app,
      {
        mixinName: 'app-v001',
        reason: 'user reason',
      },
    );
  });

  it('renders actions eligible for the selected server groups', async () => {
    renderDetails();

    await userEvent.click(screen.getByRole('button', { name: 'Actions' }));
    const menu = screen.getByRole('menu');

    expect(within(menu).getByText('Destroy')).toBeInTheDocument();
    expect(within(menu).getByText('Disable')).toBeInTheDocument();
    expect(within(menu).queryByText('Enable')).not.toBeInTheDocument();
  });

  it('clears server group multiselect on unmount only when more than one group is selected', () => {
    const firstRender = renderDetails();

    firstRender.unmount();

    expect(ClusterState.multiselectModel.clearAllServerGroups).not.toHaveBeenCalled();

    ClusterState.multiselectModel.serverGroups = [
      selectedServerGroup,
      { ...selectedServerGroup, name: 'app-v002' },
    ] as any;
    const secondRender = renderDetails();

    secondRender.unmount();

    expect(ClusterState.multiselectModel.clearAllServerGroups).toHaveBeenCalled();
  });
});
