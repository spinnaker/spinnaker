import { act, render, screen } from '@testing-library/react';
import React from 'react';

import { RenderWhenVisible } from './RenderWhenVisible';

let observerCallback: IntersectionObserverCallback;

class IntersectionObserverStub implements IntersectionObserver {
  public readonly root = null;
  public readonly rootMargin = '';
  public readonly thresholds = [];
  public disconnect = vi.fn();
  public observe = vi.fn();
  public takeRecords = vi.fn().mockReturnValue([]);
  public unobserve = vi.fn();

  constructor(callback: IntersectionObserverCallback) {
    observerCallback = callback;
  }
}

const entry = (isIntersecting: boolean, height = 100) =>
  ({ isIntersecting, boundingClientRect: { height } } as IntersectionObserverEntry);

function emitVisibility(isIntersecting: boolean, height?: number) {
  act(() => observerCallback([entry(isIntersecting, height)], {} as IntersectionObserver));
}

describe('<RenderWhenVisible />', () => {
  beforeEach(() => {
    window.IntersectionObserver = IntersectionObserverStub;
  });

  it('hides and renders content as intersection visibility changes', () => {
    render(<RenderWhenVisible initiallyVisible={true} placeholderHeight={100} render={() => <span>content</span>} />);
    expect(screen.getByText('content')).toBeInTheDocument();

    emitVisibility(false, 120);
    expect(screen.queryByText('content')).not.toBeInTheDocument();

    emitVisibility(true);
    expect(screen.getByText('content')).toBeInTheDocument();
  });

  it('keeps visible content rendered when hiding is disabled', () => {
    render(
      <RenderWhenVisible
        disableHide={true}
        initiallyVisible={true}
        placeholderHeight={100}
        render={() => <span>content</span>}
      />,
    );

    emitVisibility(false, 120);

    expect(screen.getByText('content')).toBeInTheDocument();
  });

  it('renders zero-height content immediately', () => {
    render(<RenderWhenVisible placeholderHeight={0} render={() => <span>content</span>} />);

    expect(screen.getByText('content')).toBeInTheDocument();
  });
});
