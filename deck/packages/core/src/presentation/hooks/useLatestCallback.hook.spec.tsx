import { renderHookHarness } from '../../utils/testUtils/hookHarness';
import { useLatestCallback } from './useLatestCallback.hook';

describe('useLatestCallback', () => {
  it('should give back a stable function reference when the callback argument changes', () => {
    const rendered = renderHookHarness(({ callback }) => useLatestCallback(callback), {
      callback: () => 'first',
    });
    const firstCallback = rendered.result.current;

    rendered.rerenderHook({ callback: () => 'second' });

    expect(rendered.result.current).toBe(firstCallback);

    const secondCallback = rendered.result.current;
    rendered.rerenderHook({ callback: () => 'third' });

    expect(rendered.result.current).toBe(secondCallback);
  });

  it('should always call the latest callback argument', () => {
    const initialCallback = vi.fn();
    const rendered = renderHookHarness(({ callback }) => useLatestCallback(callback), { callback: initialCallback });

    rendered.result.current();
    expect(initialCallback).toHaveBeenCalledTimes(1);

    const updatedCallback = vi.fn();
    rendered.rerenderHook({ callback: updatedCallback });

    rendered.result.current();
    expect(initialCallback).toHaveBeenCalledTimes(1);
    expect(updatedCallback).toHaveBeenCalledTimes(1);
  });

  it('should pass through the arguments/return value of the original callback', () => {
    const rendered = renderHookHarness(({ callback }) => useLatestCallback(callback), {
      callback: (value: string) => `Hello ${value}`,
    });

    const returnValue = rendered.result.current('World');
    expect(returnValue).toBe('Hello World');
  });
});
