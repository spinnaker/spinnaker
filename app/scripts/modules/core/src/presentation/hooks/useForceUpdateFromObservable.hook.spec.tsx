import React from 'react';
import { mount } from 'enzyme';
import { Subject } from 'rxjs';
import { useForceUpdateFromObservable } from './useForceUpdateFromObservable.hook';

describe('useForceUpdateFromObservable', () => {
  let renderCounts = 0;

  const TestComponent = ({ stream }: { stream: Subject<void> }) => {
    useForceUpdateFromObservable(stream);
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
