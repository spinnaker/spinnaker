import { renderHookHarness } from '../../utils/testUtils/hookHarness';
import { usePrevious } from './usePrevious.hook';

describe('usePrevious', () => {
  it('should give back undefined on initial mount', () => {
    const rendered = renderHookHarness(({ value }) => usePrevious(value), { value: 'first value' });

    expect(rendered.result.current).toBe(undefined);
  });

  it('should give back the value before the most recent render', () => {
    const rendered = renderHookHarness(({ value }) => usePrevious(value), { value: 'first value' });

    rendered.rerenderHook({ value: 'second value' });

    expect(rendered.result.current).toBe('first value');

    rendered.rerenderHook({ value: 'third value' });

    expect(rendered.result.current).toBe('second value');
  });
});
