import React from 'react';
import { shallow } from 'enzyme';

import { ModalBody, ReactModal, Spinner } from '@spinnaker/core';

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
    dismissModal: jasmine.createSpy('dismissModal'),
    resolveModal: jasmine.createSpy('resolveModal'),
  };

  const modalBody = (wrapper: any) => shallow(<div>{wrapper.find(ModalBody).prop('children')}</div>);

  it('opens the React events modal from the events link', () => {
    const show = spyOn(ReactModal, 'show');
    const wrapper = shallow(<EventsLink serverGroup={serverGroup} />);

    wrapper.find('a').simulate('click', { preventDefault: jasmine.createSpy('preventDefault') });

    expect(show).toHaveBeenCalledOnceWith(EcsServerGroupEventsModal, { serverGroup });
  });

  it('renders loading while events are pending', () => {
    spyOn(ServerGroupEventsReader, 'getEvents').and.returnValue(new Promise(() => undefined));

    const wrapper = shallow(<EcsServerGroupEventsModal {...modalProps} />);

    expect(wrapper.find(Spinner).exists()).toBe(true);
  });

  it('renders an error when the reader rejects', async () => {
    spyOn(ServerGroupEventsReader, 'getEvents').and.returnValue(Promise.reject(new Error('failed')));
    const wrapper = shallow(<EcsServerGroupEventsModal {...modalProps} />);

    await Promise.resolve();
    wrapper.update();

    expect(modalBody(wrapper).text()).toContain('There was an error loading events for fnord-main-v001');
  });

  it('renders an empty state when no events are returned', async () => {
    spyOn(ServerGroupEventsReader, 'getEvents').and.returnValue(Promise.resolve([]));
    const wrapper = shallow(<EcsServerGroupEventsModal {...modalProps} />);

    await Promise.resolve();
    wrapper.update();

    expect(modalBody(wrapper).text()).toContain('No ECS events found for fnord-main-v001');
  });

  it('renders ECS events and status labels', async () => {
    spyOn(ServerGroupEventsReader, 'getEvents').and.returnValue(
      Promise.resolve([
        { id: 'one', createdAt: 1710000000000, message: 'service reached steady state', status: 'Success' },
        { id: 'two', createdAt: 1710000001000, message: 'deployment transitioning', status: 'Transition' },
      ]),
    );
    const wrapper = shallow(<EcsServerGroupEventsModal {...modalProps} />);

    await Promise.resolve();
    wrapper.update();

    const body = modalBody(wrapper);
    expect(body.text()).toContain('service reached steady state');
    expect(body.text()).toContain('deployment transitioning');
    expect(body.find('.label-success').text()).toBe('Success');
    expect(body.find('.label-info').text()).toBe('Transition');
  });

  it('ignores stale responses and responses received after unmount', async () => {
    const first = deferred<any[]>();
    const second = deferred<any[]>();
    const getEvents = spyOn(ServerGroupEventsReader, 'getEvents').and.callFake((group: any) =>
      group.name === 'first' ? first.promise : second.promise,
    );
    const wrapper = shallow(
      <EcsServerGroupEventsModal {...modalProps} serverGroup={{ ...serverGroup, name: 'first' }} />,
    );
    wrapper.setProps({ serverGroup: { ...serverGroup, name: 'second' } });

    first.resolve([{ id: 'old', message: 'stale event', status: 'Success', createdAt: 1 }]);
    await first.promise;
    await Promise.resolve();
    wrapper.update();
    expect(modalBody(wrapper).text()).not.toContain('stale event');

    const instance = wrapper.instance() as EcsServerGroupEventsModal;
    const setState = spyOn(instance, 'setState').and.callThrough();
    wrapper.unmount();
    second.resolve([{ id: 'new', message: 'late event', status: 'Success', createdAt: 2 }]);
    await second.promise;
    await Promise.resolve();

    expect(getEvents).toHaveBeenCalledTimes(2);
    expect(setState).not.toHaveBeenCalled();
  });
});
