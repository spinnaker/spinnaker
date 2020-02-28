import React from 'react';
import { mount } from 'enzyme';
import { useIsFirstRender } from './useIsFirstRender.hook';

describe('useIsFirstRender', () => {
  const TestComponent = ({ callback }: { callback: (isFirst: boolean) => void }) => {
    const isFirstRender = useIsFirstRender();
    callback(isFirstRender);
    return <i />;
  };

  it('should rerender when the mutation stream changes', () => {
    let isFirstRender: boolean;
    const mounted = mount(<TestComponent callback={isFirst => (isFirstRender = isFirst)} />);

    expect(isFirstRender).toBe(true);
    mounted.setProps({});
    expect(isFirstRender).toBe(false);
    mounted.setProps({});
    expect(isFirstRender).toBe(false);
  });
});
