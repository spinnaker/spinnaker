import React, { RefObject } from 'react';
import { mount } from 'enzyme';
import { useMountStatusRef } from './useMountStatusRef.hook';

describe('useMountStatusRef', () => {
  type TestCallback = (statusDuringRender: string, status: RefObject<string>) => void;
  const TestComponent = ({ callback }: { callback: TestCallback }) => {
    const mountStatusRef = useMountStatusRef();
    callback(mountStatusRef.current, mountStatusRef);
    return <i />;
  };

  it('current should follow the mount status of the component', async () => {
    let mountStatusDuringRender: string;
    let mountStatus: RefObject<string>;
    const mounted = mount(
      <TestComponent
        callback={(statusDuringRender, statusRef) => {
          mountStatusDuringRender = statusDuringRender;
          mountStatus = statusRef;
        }}
      />,
    );

    expect(mountStatusDuringRender).toBe('FIRST_RENDER');
    expect(mountStatus.current).toBe('MOUNTED');
    mounted.setProps({});
    expect(mountStatusDuringRender).toBe('MOUNTED');
    expect(mountStatus.current).toBe('MOUNTED');
    mounted.unmount();
    expect(mountStatusDuringRender).toBe('MOUNTED');
    expect(mountStatus.current).toBe('UNMOUNTED');
  });
});
