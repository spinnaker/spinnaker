import { renderHookHarness } from '../../utils/testUtils/hookHarness';
import { useDeepObjectDiff } from './useDeepObjectDiff.hook';

describe('useDeepObjectDiff', () => {
  it('changes its return value when the object has changed between renders', () => {
    const rendered = renderHookHarness((props) => useDeepObjectDiff(props), { prop: 123 });
    expect(rendered.result.current).toBe(1);

    rendered.rerenderHook({ prop: 123 });
    rendered.rerenderHook({ prop: 123 });
    rendered.rerenderHook({ prop: 123 });
    rendered.rerenderHook({ prop: 123 });
    expect(rendered.result.current).toBe(1);

    rendered.rerenderHook({ prop: 456 });
    expect(rendered.result.current).toBe(2);
  });
});
