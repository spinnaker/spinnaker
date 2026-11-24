import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { AccountService, DeckRuntimeContext, ReactModal, TaskReader } from '@spinnaker/core';
import { ModalContext } from '../../../../../core/src/presentation/modal/ModalContext';
import { renderWithRouter } from '../../../../../core/src/utils/testUtils/rtl';

import { EcsResizeServerGroupModal, validateEcsResizeValues } from './index';

describe('EcsResizeServerGroupModal', () => {
  const serverGroup = {
    account: 'test-account',
    capacity: { desired: 4, max: 8, min: 2 },
    name: 'fnord-main-v004',
    region: 'eu-west-1',
  };

  function application(attributes: any = {}) {
    return {
      attributes,
      getDataSource: vi.fn(),
      serverGroups: { refresh: vi.fn() },
    } as any;
  }

  function props(app = application()) {
    return {
      application: app,
      closeModal: vi.fn(),
      dismissModal: vi.fn(),
      serverGroup: serverGroup as any,
    };
  }

  function renderModal(modalProps = props(), runtimeServices: any = {}) {
    return renderWithRouter(
      <ModalContext.Provider value={{ onRequestClose: vi.fn() }}>
        <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>
          <EcsResizeServerGroupModal {...modalProps} />
        </DeckRuntimeContext.Provider>
      </ModalContext.Provider>,
    );
  }

  beforeEach(() => {
    vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(true);
  });

  it('validates non-negative min, max, and desired capacities in range', () => {
    expect(validateEcsResizeValues({ capacity: { desired: 4, max: 8, min: -1 } })).toEqual({
      capacity: { min: 'Min cannot be negative' },
    });
    expect(validateEcsResizeValues({ capacity: { desired: 4, max: 1, min: 2 } })).toEqual({
      capacity: { max: 'Max cannot be smaller than Min', min: 'Min cannot be larger than Max' },
    });
    expect(validateEcsResizeValues({ capacity: { desired: 1, max: 8, min: 2 } })).toEqual({
      capacity: { desired: 'Desired cannot be smaller than Min' },
    });
    expect(validateEcsResizeValues({ capacity: { desired: 9, max: 8, min: 2 } })).toEqual({
      capacity: { desired: 'Desired cannot be larger than Max' },
    });
    expect(validateEcsResizeValues({ capacity: { desired: 4, max: 8, min: 2 } })).toEqual({});
  });

  it('submits the exact shared resize writer contract through its task monitor', async () => {
    const app = application({ platformHealthOnly: true, platformHealthOnlyShowOverride: true });
    const resizeServerGroup = vi.fn().mockResolvedValue({ id: 'task-id' });
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue({} as any);
    renderModal(props(app), { serverGroupWriter: { resizeServerGroup } });

    fireEvent.change(screen.getByRole('spinbutton', { name: 'Minimum capacity' }), { target: { value: '3' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Maximum capacity' }), { target: { value: '10' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Desired capacity' }), { target: { value: '6' } });
    await userEvent.type(screen.getByRole('textbox', { name: 'Reason' }), '  preserve this resize reason exactly  ');
    await userEvent.type(screen.getByRole('textbox', { name: 'Confirm account test-account' }), 'test-account');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Submit' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(resizeServerGroup).toHaveBeenCalledExactlyOnceWith(serverGroup, app, {
      capacity: { desired: 6, max: 10, min: 3 },
      interestingHealthProviderNames: ['Ecs'],
      reason: '  preserve this resize reason exactly  ',
    });
    await waitFor(() => expect(app.serverGroups.refresh).toHaveBeenCalledExactlyOnceWith());
  });

  it('does not submit invalid or unverified resize commands', async () => {
    const resizeServerGroup = vi.fn();
    renderModal(props(), { serverGroupWriter: { resizeServerGroup } });
    const submit = screen.getByRole('button', { name: 'Submit' });

    expect(submit).toBeDisabled();
    fireEvent.change(screen.getByRole('spinbutton', { name: 'Desired capacity' }), { target: { value: '9' } });
    await userEvent.type(screen.getByRole('textbox', { name: 'Confirm account test-account' }), 'test-account');

    expect(await screen.findByText('Desired cannot be larger than Max')).toBeInTheDocument();
    expect(submit).toBeDisabled();
    fireEvent.click(submit);
    expect(resizeServerGroup).not.toHaveBeenCalled();
  });

  it('renders task, capacity, verification, reason, and ECS platform-health controls', () => {
    const app = application({ platformHealthOnly: true, platformHealthOnlyShowOverride: true });
    renderModal(props(app));

    expect(screen.getByText('Resize fnord-main-v004')).toBeInTheDocument();
    expect(screen.getByRole('spinbutton', { name: 'Minimum capacity' })).toHaveValue(2);
    expect(screen.getByRole('spinbutton', { name: 'Maximum capacity' })).toHaveValue(8);
    expect(screen.getByRole('spinbutton', { name: 'Desired capacity' })).toHaveValue(4);
    expect(screen.getByRole('textbox', { name: 'Reason' })).toBeInTheDocument();
    expect(screen.getByText(/Type the name of the account/)).toHaveTextContent('test-account');
    expect(screen.getByRole('checkbox', { name: 'Consider only Ecs health' })).toBeChecked();
  });

  it('exports a show primitive for later actions integration', () => {
    const show = vi.spyOn(ReactModal, 'show').mockReturnValue(Promise.resolve() as any);
    const modalProps = props();
    const runtimeServices = {} as any;

    EcsResizeServerGroupModal.show(modalProps, runtimeServices);

    expect(show).toHaveBeenCalledExactlyOnceWith(EcsResizeServerGroupModal, modalProps, undefined, runtimeServices);
  });
});
