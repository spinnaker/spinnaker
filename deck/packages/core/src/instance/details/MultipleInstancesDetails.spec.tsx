import { fireEvent, render, screen, within } from '@testing-library/react';
import { hashLocationPlugin, servicesPlugin, UIRouterContext, UIRouterReact, UIViewContext } from '@uirouter/react';
import React from 'react';
import type { Mock } from 'vitest';

import { MultipleInstancesDetails } from './MultipleInstancesDetails';
import { AccountService } from '../../account';
import { DeckRuntimeContext } from '../../bootstrap/DeckRuntimeContext';
import { ProviderSelectionService } from '../../cloudProvider/providerSelection/ProviderSelectionService';
import { ConfirmationModalService } from '../../confirmationModal';
import { InstanceWriter } from '../instance.write.service';
import { ClusterState } from '../../state';
import { setupUser } from '../../utils/testUtils';

describe('<MultipleInstancesDetails />', () => {
  const providerServiceDelegate = {} as any;
  let previousMultiselectModel: any;
  let router: UIRouterReact;

  const app = {
    serverGroups: {
      data: [
        {
          account: 'prod',
          name: 'app-v001',
          region: 'us-west-2',
          loadBalancers: ['lb-a'],
          instances: [
            {
              id: 'i-1',
              name: 'instance-one',
              availabilityZone: 'us-west-2a',
              healthState: 'Up',
              health: [{ type: 'Discovery', state: 'Up' }],
            },
          ],
        },
      ],
      onRefresh: vi.fn().mockReturnValue(() => null),
    },
  } as any;

  const renderDetails = () =>
    render(
      <UIRouterContext.Provider value={router}>
        <UIViewContext.Provider
          value={{
            fqn: 'application.insight.multipleInstances',
            context: router.stateRegistry.get('application.insight.multipleInstances') as any,
          }}
        >
          <DeckRuntimeContext.Provider value={{ services: { providerServiceDelegate } } as any}>
            <MultipleInstancesDetails app={app} />
          </DeckRuntimeContext.Provider>
        </UIViewContext.Provider>
      </UIRouterContext.Provider>,
    );

  beforeEach(() => {
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    ['application', 'application.insight', 'application.insight.multipleInstances'].forEach((name) => {
      router.stateRegistry.register({ name, url: `/${name.split('.').pop()}` } as any);
    });

    previousMultiselectModel = ClusterState.multiselectModel;
    ClusterState.multiselectModel = {
      instanceGroups: [],
      instancesStream: { subscribe: () => null },
      deselectAllInstances: () => null,
    } as any;

    vi.spyOn(AccountService, 'challengeDestructiveActions').mockReturnValue(Promise.resolve(false));
    vi.spyOn(ProviderSelectionService, 'isDisabled').mockReturnValue(Promise.resolve(false));
    vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    vi.spyOn(InstanceWriter, 'terminateInstances').mockReturnValue(Promise.resolve({}) as any);
    vi.spyOn(ClusterState.multiselectModel.instancesStream, 'subscribe').mockImplementation((callback: any) => {
      callback();
      return { unsubscribe: vi.fn() } as any;
    });
    vi.spyOn(ClusterState.multiselectModel, 'deselectAllInstances').mockReturnValue(undefined);
    ClusterState.multiselectModel.instanceGroups = [
      {
        account: 'prod',
        cloudProvider: 'aws',
        instanceIds: ['i-1'],
        region: 'us-west-2',
        serverGroup: 'app-v001',
      },
    ] as any;
  });

  afterEach(() => {
    ClusterState.multiselectModel = previousMultiselectModel;
    router.dispose();
  });

  it('renders selected instances grouped by server group', () => {
    const { container } = renderDetails();

    expect(screen.getByRole('heading', { name: '1 Instance' })).toBeInTheDocument();
    expect(screen.getByText('app-v001')).toBeInTheDocument();
    expect(screen.getByText('prod')).toBeInTheDocument();
    expect(screen.getByText(/us-west-2/)).toBeInTheDocument();
    expect(screen.getByText('instance-one')).toBeInTheDocument();
    expect(container.querySelectorAll('.multiple-instance-server-group')).toHaveLength(1);
  });

  it('renders server groups in the shared collapsible section with the legacy wrapper element', () => {
    const { container } = renderDetails();

    expect(screen.getByText('Server Groups')).toBeInTheDocument();
    expect(
      container.querySelectorAll('.collapsible-section .content-body .multiple-instance-server-group'),
    ).toHaveLength(1);
    expect(screen.getByText('instance-one')).toBeVisible();
  });

  it('opens terminate confirmation using selected groups', async () => {
    const user = setupUser();
    renderDetails();

    await user.click(screen.getByRole('button', { name: 'Actions' }));
    await user.click(screen.getByText('Terminate', { selector: 'a' }));

    expect(ConfirmationModalService.confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        buttonText: 'Terminate 1 instance',
        textToVerify: '1',
      }),
    );
    const confirmation = (ConfirmationModalService.confirm as Mock).mock.lastCall[0];

    confirmation.submitMethod();

    expect(InstanceWriter.terminateInstances).toHaveBeenCalledWith(
      [
        {
          account: 'prod',
          cloudProvider: 'aws',
          instanceIds: ['i-1'],
          instances: [
            {
              availabilityZone: 'us-west-2a',
              health: [{ state: 'Up', type: 'Discovery' }],
              healthState: 'Up',
              id: 'i-1',
              name: 'instance-one',
            },
          ],
          loadBalancers: ['lb-a'],
          region: 'us-west-2',
          serverGroup: 'app-v001',
        },
      ],
      app,
      providerServiceDelegate,
    );
  });

  it('closes the actions dropdown when an action is selected', () => {
    const { container } = renderDetails();
    const toggle = screen.getByRole('button', { name: 'Actions' });

    // react-overlays' RootCloseWrapper relies on `window.event` to ignore the opening click, which jsdom does not
    // provide for user-event's pointer sequence, so a single synthetic click is used to open the menu.
    fireEvent.click(toggle);
    expect(container.querySelector('.dropdown')).toHaveClass('open');
    expect(toggle).toHaveAttribute('aria-expanded', 'true');

    fireEvent.click(screen.getByText('Terminate', { selector: 'a' }));

    expect(container.querySelector('.dropdown')).not.toHaveClass('open');
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
  });

  it('renders only actions eligible for the selected instances', async () => {
    const user = setupUser();
    renderDetails();

    await user.click(screen.getByRole('button', { name: 'Actions' }));
    const menu = screen.getByRole('menu');

    expect(within(menu).getByText('Disable in Discovery')).toBeInTheDocument();
    expect(within(menu).getByText('Register with Load Balancer')).toBeInTheDocument();
    expect(within(menu).getByText('Reboot')).toBeInTheDocument();
    expect(within(menu).getByText('Terminate and Shrink Server Groups')).toBeInTheDocument();
    expect(within(menu).queryByText('Enable in Discovery')).not.toBeInTheDocument();
    expect(within(menu).queryByText('Deregister from Load Balancer')).not.toBeInTheDocument();
  });

  it('clears selected instances on unmount', () => {
    const { unmount } = renderDetails();

    unmount();

    expect(ClusterState.multiselectModel.deselectAllInstances).toHaveBeenCalled();
  });
});
