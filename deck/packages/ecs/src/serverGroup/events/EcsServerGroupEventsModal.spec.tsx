import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';

import { ReactModal } from '@spinnaker/core';
import { ModalContext } from '../../../../core/src/presentation/modal/ModalContext';

import { EcsServerGroupEventsModal } from './EcsServerGroupEventsModal';
import { EventsLink } from './EventsLink';
import { ServerGroupEventsReader } from './serverGroupEventsReader.service';

function deferred<T>() {
  let resolve: (value: T) => void;
  const promise = new Promise<T>((res) => (resolve = res));
  return { promise, resolve: resolve! };
}

describe('ECS server group events', () => {
  const serverGroup = { name: 'fnord-main-v001' } as any;
  const modalProps = {
    serverGroup,
    dismissModal: vi.fn(),
    resolveModal: vi.fn(),
  };
  const modal = (props = modalProps) => (
    <ModalContext.Provider value={{ onRequestClose: vi.fn() }}>
      <EcsServerGroupEventsModal {...props} />
    </ModalContext.Provider>
  );

  it('opens the React events modal from the events link', () => {
    const show = vi.spyOn(ReactModal, 'show').mockReturnValue(undefined);
    render(<EventsLink serverGroup={serverGroup} />);

    fireEvent.click(screen.getByRole('link', { name: 'View Events' }));

    expect(show).toHaveBeenCalledExactlyOnceWith(EcsServerGroupEventsModal, { serverGroup });
  });

  it('renders loading while events are pending', () => {
    vi.spyOn(ServerGroupEventsReader, 'getEvents').mockReturnValue(new Promise(() => undefined));

    const { container } = render(modal());

    expect(screen.getByText('Server Group Events for fnord-main-v001')).toBeInTheDocument();
    expect(container.querySelector('.load')).toBeInTheDocument();
  });

  it('renders an error when the reader rejects', async () => {
    vi.spyOn(ServerGroupEventsReader, 'getEvents').mockReturnValue(Promise.reject(new Error('failed')));
    render(modal());

    expect(await screen.findByText(/There was an error loading events for fnord-main-v001/)).toBeInTheDocument();
  });

  it('renders an empty state when no events are returned', async () => {
    vi.spyOn(ServerGroupEventsReader, 'getEvents').mockReturnValue(Promise.resolve([]));
    render(modal());

    expect(await screen.findByText(/No ECS events found for fnord-main-v001/)).toBeInTheDocument();
  });

  it('renders ECS events and status labels', async () => {
    vi.spyOn(ServerGroupEventsReader, 'getEvents').mockReturnValue(
      Promise.resolve([
        { id: 'one', createdAt: 1710000000000, message: 'service reached steady state', status: 'Success' },
        { id: 'two', createdAt: 1710000001000, message: 'deployment transitioning', status: 'Transition' },
      ]),
    );
    render(modal());

    expect(await screen.findByText('service reached steady state')).toBeInTheDocument();
    expect(screen.getByText('deployment transitioning')).toBeInTheDocument();
    expect(screen.getByText('Success')).toHaveClass('label-success');
    expect(screen.getByText('Transition')).toHaveClass('label-info');
  });

  it('ignores stale responses and responses received after unmount', async () => {
    const first = deferred<any[]>();
    const second = deferred<any[]>();
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const getEvents = vi
      .spyOn(ServerGroupEventsReader, 'getEvents')
      .mockImplementation((group: any) => (group.name === 'first' ? first.promise : second.promise));
    const rendered = render(modal({ ...modalProps, serverGroup: { ...serverGroup, name: 'first' } }));
    rendered.rerender(modal({ ...modalProps, serverGroup: { ...serverGroup, name: 'second' } }));

    await act(async () => first.resolve([{ id: 'old', message: 'stale event', status: 'Success', createdAt: 1 }]));
    expect(screen.queryByText('stale event')).not.toBeInTheDocument();

    rendered.unmount();
    await act(async () => second.resolve([{ id: 'new', message: 'late event', status: 'Success', createdAt: 2 }]));

    expect(getEvents).toHaveBeenCalledTimes(2);
    expect(consoleError.mock.calls.flat().join(' ')).not.toMatch(/state update on an unmounted component/i);
  });
});
