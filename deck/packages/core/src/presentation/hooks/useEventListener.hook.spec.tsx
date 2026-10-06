import type { Mock } from 'vitest';

import { renderHookHarness } from '../../utils/testUtils/hookHarness';
import { useEventListener } from './useEventListener.hook';

interface IHookProps {
  element: Element;
  eventName: string;
  listener?: ((e: Event) => any) | null;
  options: AddEventListenerOptions;
}

const renderEventListener = (props: IHookProps) =>
  renderHookHarness(
    ({ element, eventName, listener, options }: IHookProps) =>
      useEventListener(element, eventName, listener ?? undefined, options),
    props,
  );

const eventListenerOptions = { capture: true };

describe('useEventListener', () => {
  let eventTarget: HTMLDivElement;
  let addEventListenerSpy: Mock;
  let removeEventListenerSpy: Mock;

  beforeEach(() => {
    eventTarget = document.createElement('div');
    addEventListenerSpy = vi.spyOn(eventTarget, 'addEventListener').mockReturnValue(undefined);
    removeEventListenerSpy = vi.spyOn(eventTarget, 'removeEventListener').mockReturnValue(undefined);
  });

  it('should call addEventListener on the target element when mounted', () => {
    renderEventListener({
      element: eventTarget,
      eventName: 'keydown',
      listener: () => null,
      options: eventListenerOptions,
    });

    expect(addEventListenerSpy).toHaveBeenCalledTimes(1);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(0);

    expect(addEventListenerSpy.mock.calls[0][0]).toBe('keydown');
    expect(addEventListenerSpy.mock.calls[0][2]).toBe(eventListenerOptions);
  });

  it('should not do anything when mounted if there is no listener prop', () => {
    renderEventListener({ element: eventTarget, eventName: 'keydown', options: eventListenerOptions });

    expect(addEventListenerSpy).toHaveBeenCalledTimes(0);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(0);
  });

  it('should call removeEventListener on the target element when unmounted', () => {
    const rendered = renderEventListener({
      element: eventTarget,
      eventName: 'keydown',
      listener: () => null,
      options: eventListenerOptions,
    });

    expect(addEventListenerSpy).toHaveBeenCalledTimes(1);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(0);

    rendered.unmount();

    expect(addEventListenerSpy).toHaveBeenCalledTimes(1);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(1);

    expect(removeEventListenerSpy.mock.calls[0][0]).toBe('keydown');
    expect(removeEventListenerSpy.mock.calls[0][2]).toBe(eventListenerOptions);
  });

  it('should call removeEventListener with the same event listener function reference', () => {
    let addedListener: any;
    let removedListener: any;
    addEventListenerSpy.mockImplementation((_: string, eventListener: () => any) => (addedListener = eventListener));
    removeEventListenerSpy.mockImplementation(
      (_: string, eventListener: () => any) => (removedListener = eventListener),
    );

    const rendered = renderEventListener({
      element: eventTarget,
      eventName: 'keydown',
      listener: () => null,
      options: eventListenerOptions,
    });

    rendered.unmount();

    expect(addEventListenerSpy).toHaveBeenCalledTimes(1);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(1);

    expect(addedListener).toBe(removedListener);
  });

  it('should call the latest listener prop', () => {
    let addedListener: any;
    addEventListenerSpy.mockImplementation((_: string, eventListener: () => any) => (addedListener = eventListener));

    const initialListener = vi.fn();

    const rendered = renderEventListener({
      element: eventTarget,
      eventName: 'keydown',
      listener: initialListener,
      options: eventListenerOptions,
    });

    addedListener();
    expect(initialListener).toHaveBeenCalledTimes(1);

    const updatedListener = vi.fn();
    rendered.rerenderHook({
      element: eventTarget,
      eventName: 'keydown',
      listener: updatedListener,
      options: eventListenerOptions,
    });

    addedListener();
    expect(initialListener).toHaveBeenCalledTimes(1);
    expect(updatedListener).toHaveBeenCalledTimes(1);
  });

  it('should call removeEventListener with the same event listener function reference after updating the listener prop', () => {
    let addedListener: any;
    let removedListener: any;
    addEventListenerSpy.mockImplementation((_: string, eventListener: () => any) => (addedListener = eventListener));
    removeEventListenerSpy.mockImplementation(
      (_: string, eventListener: () => any) => (removedListener = eventListener),
    );

    const rendered = renderEventListener({
      element: eventTarget,
      eventName: 'keydown',
      listener: () => 'initial',
      options: eventListenerOptions,
    });

    rendered.rerenderHook({
      element: eventTarget,
      eventName: 'keydown',
      listener: () => 'updated',
      options: eventListenerOptions,
    });

    rendered.unmount();

    expect(addEventListenerSpy).toHaveBeenCalledTimes(1);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(1);

    expect(addedListener).toBe(removedListener);
  });

  it('should add and remove the same listener reference when the listener prop is added/removed', () => {
    let addedListener: any;
    let removedListener: any;
    addEventListenerSpy.mockImplementation((_: string, eventListener: () => any) => (addedListener = eventListener));
    removeEventListenerSpy.mockImplementation(
      (_: string, eventListener: () => any) => (removedListener = eventListener),
    );

    const rendered = renderEventListener({
      element: eventTarget,
      eventName: 'keydown',
      listener: () => 'first',
      options: eventListenerOptions,
    });

    expect(addEventListenerSpy).toHaveBeenCalledTimes(1);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(0);
    rendered.rerenderHook({
      element: eventTarget,
      eventName: 'keydown',
      listener: null,
      options: eventListenerOptions,
    });

    expect(addEventListenerSpy).toHaveBeenCalledTimes(1);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(1);

    expect(addedListener).toBe(removedListener);

    rendered.rerenderHook({
      element: eventTarget,
      eventName: 'keydown',
      listener: () => 'second',
      options: eventListenerOptions,
    });

    expect(addEventListenerSpy).toHaveBeenCalledTimes(2);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(1);

    expect(addedListener).toBe(removedListener);

    rendered.rerenderHook({
      element: eventTarget,
      eventName: 'keydown',
      listener: null,
      options: eventListenerOptions,
    });

    expect(addEventListenerSpy).toHaveBeenCalledTimes(2);
    expect(removeEventListenerSpy).toHaveBeenCalledTimes(2);

    expect(addedListener).toBe(removedListener);
  });
});
