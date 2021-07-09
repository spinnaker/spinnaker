import React from 'react';
import { mount } from 'enzyme';
import { useEventListener } from './useEventListener.hook';

const TestComponent = ({
  element,
  eventName,
  listener,
  options,
}: {
  element: Element;
  eventName: string;
  listener?: (e: Event) => any;
  options: AddEventListenerOptions;
}) => {
  useEventListener(element, eventName, listener, options);
  return null as JSX.Element;
};

const eventListenerOptions = { capture: true };

describe('useEventListener', () => {
  let eventTarget: HTMLDivElement;
  let addEventListenerSpy: jasmine.Spy;
  let removeEventListenerSpy: jasmine.Spy;

  beforeEach(() => {
    eventTarget = document.createElement('div');
    addEventListenerSpy = spyOn(eventTarget, 'addEventListener').and.returnValue(undefined);
    removeEventListenerSpy = spyOn(eventTarget, 'removeEventListener').and.returnValue(undefined);
  });

  it('should call addEventListener on the target element when mounted', () => {
    mount(
      <TestComponent element={eventTarget} eventName="keydown" listener={() => null} options={eventListenerOptions} />,
    );

    expect(addEventListenerSpy).toHaveBeenCalledTimes(1);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(0);

    expect(addEventListenerSpy.calls.argsFor(0)[0]).toBe('keydown');
    expect(addEventListenerSpy.calls.argsFor(0)[2]).toBe(eventListenerOptions);
  });

  it('should not do anything when mounted if there is no listener prop', () => {
    mount(<TestComponent element={eventTarget} eventName="keydown" options={eventListenerOptions} />);

    expect(addEventListenerSpy).toHaveBeenCalledTimes(0);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(0);
  });

  it('should call removeEventListener on the target element when unmounted', () => {
    const component = mount(
      <TestComponent element={eventTarget} eventName="keydown" listener={() => null} options={eventListenerOptions} />,
    );

    expect(addEventListenerSpy).toHaveBeenCalledTimes(1);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(0);

    component.unmount();

    expect(addEventListenerSpy).toHaveBeenCalledTimes(1);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(1);

    expect(removeEventListenerSpy.calls.argsFor(0)[0]).toBe('keydown');
    expect(removeEventListenerSpy.calls.argsFor(0)[2]).toBe(eventListenerOptions);
  });

  it('should call removeEventListener with the same event listener function reference', () => {
    let addedListener: any;
    let removedListener: any;
    addEventListenerSpy.and.callFake((_: string, eventListener: () => any) => (addedListener = eventListener));
    removeEventListenerSpy.and.callFake((_: string, eventListener: () => any) => (removedListener = eventListener));

    const component = mount(
      <TestComponent element={eventTarget} eventName="keydown" listener={() => null} options={eventListenerOptions} />,
    );

    component.unmount();

    expect(addEventListenerSpy).toHaveBeenCalledTimes(1);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(1);

    expect(addedListener).toBe(removedListener);
  });

  it('should call the latest listener prop', () => {
    let addedListener: any;
    addEventListenerSpy.and.callFake((_: string, eventListener: () => any) => (addedListener = eventListener));

    const initialListener = jasmine.createSpy('initialListener', () => 'initial');

    const component = mount(
      <TestComponent
        element={eventTarget}
        eventName="keydown"
        listener={initialListener}
        options={eventListenerOptions}
      />,
    );

    addedListener();
    expect(initialListener).toHaveBeenCalledTimes(1);

    const updatedListener = jasmine.createSpy('updatedListener', () => 'updated');
    component.setProps({ listener: updatedListener });

    addedListener();
    expect(initialListener).toHaveBeenCalledTimes(1);
    expect(updatedListener).toHaveBeenCalledTimes(1);
  });

  it('should call removeEventListener with the same event listener function reference after updating the listener prop', () => {
    let addedListener: any;
    let removedListener: any;
    addEventListenerSpy.and.callFake((_: string, eventListener: () => any) => (addedListener = eventListener));
    removeEventListenerSpy.and.callFake((_: string, eventListener: () => any) => (removedListener = eventListener));

    const component = mount(
      <TestComponent
        element={eventTarget}
        eventName="keydown"
        listener={() => 'initial'}
        options={eventListenerOptions}
      />,
    );

    component.setProps({ listener: () => 'updated' });

    component.unmount();

    expect(addEventListenerSpy).toHaveBeenCalledTimes(1);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(1);

    expect(addedListener).toBe(removedListener);
  });

  it('should add and remove the same listener reference when the listener prop is added/removed', () => {
    let addedListener: any;
    let removedListener: any;
    addEventListenerSpy.and.callFake((_: string, eventListener: () => any) => (addedListener = eventListener));
    removeEventListenerSpy.and.callFake((_: string, eventListener: () => any) => (removedListener = eventListener));

    const component = mount(
      <TestComponent
        element={eventTarget}
        eventName="keydown"
        listener={() => 'first'}
        options={eventListenerOptions}
      />,
    );

    expect(addEventListenerSpy).toHaveBeenCalledTimes(1);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(0);
    component.setProps({ listener: null });

    expect(addEventListenerSpy).toHaveBeenCalledTimes(1);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(1);

    expect(addedListener).toBe(removedListener);

    component.setProps({ listener: () => 'second' });

    expect(addEventListenerSpy).toHaveBeenCalledTimes(2);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(1);

    expect(addedListener).toBe(removedListener);

    component.setProps({ listener: null });

    expect(addEventListenerSpy).toHaveBeenCalledTimes(2);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(2);

    expect(addedListener).toBe(removedListener);
  });
});
