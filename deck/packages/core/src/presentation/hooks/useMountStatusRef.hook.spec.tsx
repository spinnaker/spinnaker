import { renderHookHarness } from '../../utils/testUtils/hookHarness';
import { useMountStatusRef } from './useMountStatusRef.hook';

describe('useMountStatusRef', () => {
  it('current should follow the mount status of the component', () => {
    const rendered = renderHookHarness(() => {
      const mountStatusRef = useMountStatusRef();
      return { statusDuringRender: mountStatusRef.current, mountStatusRef };
    }, {});
    const mountStatusRef = rendered.result.current.mountStatusRef;

    expect(rendered.result.current.statusDuringRender).toBe('FIRST_RENDER');
    expect(mountStatusRef.current).toBe('MOUNTED');
    rendered.rerenderHook({});
    expect(rendered.result.current.statusDuringRender).toBe('MOUNTED');
    expect(mountStatusRef.current).toBe('MOUNTED');
    rendered.unmount();
    expect(rendered.result.current.statusDuringRender).toBe('MOUNTED');
    expect(mountStatusRef.current).toBe('UNMOUNTED');
  });
});
