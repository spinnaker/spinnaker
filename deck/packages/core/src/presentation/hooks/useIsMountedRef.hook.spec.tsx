import { renderHookHarness } from '../../utils/testUtils/hookHarness';
import { useIsMountedRef } from '..';

describe('useIsMountedRef hook', () => {
  function renderMountStatus() {
    let renderCount = 0;
    const rendered = renderHookHarness(() => {
      renderCount += 1;
      const ref = useIsMountedRef();
      return { ref, isMountedInRender: ref.current };
    }, {});
    return { ...rendered, getRenderCount: () => renderCount };
  }

  it('ref.current is false inside the initial render', () => {
    const rendered = renderMountStatus();
    expect(rendered.getRenderCount()).toBe(1);
    expect(rendered.result.current.isMountedInRender).toBe(false);
  });

  it('ref.current is true after the initial render', () => {
    const rendered = renderMountStatus();
    expect(rendered.getRenderCount()).toBe(1);
    expect(rendered.result.current.ref.current).toBe(true);
  });

  it('ref.current is true during and after the next render', () => {
    const rendered = renderMountStatus();
    rendered.rerenderHook({});
    expect(rendered.getRenderCount()).toBe(2);
    expect(rendered.result.current.isMountedInRender).toBe(true);
    expect(rendered.result.current.ref.current).toBe(true);
  });

  it('ref.current remains true on subsequent renders', () => {
    const rendered = renderMountStatus();
    rendered.rerenderHook({});
    rendered.rerenderHook({});
    rendered.rerenderHook({});
    expect(rendered.getRenderCount()).toBe(4);
    expect(rendered.result.current.isMountedInRender).toBe(true);
    expect(rendered.result.current.ref.current).toBe(true);
  });

  it('ref.current is false after the component unmounts', () => {
    const rendered = renderMountStatus();
    const ref = rendered.result.current.ref;
    expect(ref.current).toBe(true);

    rendered.unmount();

    expect(ref.current).toBe(false);
  });
});
