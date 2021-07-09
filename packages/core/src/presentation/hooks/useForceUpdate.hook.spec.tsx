import React from 'react';
import { mount } from 'enzyme';
import { Subject } from 'rxjs';
import { useForceUpdate } from '..';

describe('useForceUpdate', () => {
  let renderCounts = 0;

  const TestComponent = ({ stream }: { stream: Subject<void> }) => {
    const forceUpdate = useForceUpdate();
    React.useEffect(() => {
      const subscription = stream.subscribe(() => forceUpdate());
      return () => subscription.unsubscribe();
    });
    renderCounts += 1;
    return <i />;
  };

  it('should rerender when the mutation stream changes', () => {
    const stream = new Subject<void>();
    const mounted = mount(<TestComponent stream={stream} />);

    expect(renderCounts).toBe(1);
    stream.next();
    expect(renderCounts).toBe(2);
    stream.next();
    expect(renderCounts).toBe(3);

    mounted.unmount();
    stream.next();
    expect(renderCounts).toBe(3);
  });
});
