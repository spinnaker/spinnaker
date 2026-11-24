import { render } from '@testing-library/react';
import React from 'react';

import { HealthCounts } from './HealthCounts';
import type { IInstanceCounts } from '../domain';

describe('<HealthCounts />', () => {
  const renderCounts = (container: IInstanceCounts) => render(<HealthCounts container={container} />).container;
  const count = (root: HTMLElement, selector: string) => root.querySelectorAll(selector).length;

  it('displays nothing when container has no health info', () => {
    expect(renderCounts({} as IInstanceCounts)).toBeEmptyDOMElement();
  });

  it('displays only up count when only up count is provided', () => {
    const root = renderCounts({ up: 2 } as IInstanceCounts);
    expect(count(root, 'span.counter')).toBe(1);
    expect(count(root, '.glyphicon-Up-triangle')).toBe(1);
    expect(count(root, '.glyphicon-Down-triangle')).toBe(0);
    expect(count(root, '.glyphicon-Unknown-triangle')).toBe(0);
    expect(count(root, 'span.healthy')).toBe(2);
    expect(root.querySelectorAll('span.healthy')[1]).toHaveTextContent('100%');
  });

  it('displays only down count when only down count is provided', () => {
    const root = renderCounts({ down: 2 } as IInstanceCounts);
    expect(count(root, 'span.counter')).toBe(1);
    expect(count(root, '.glyphicon-Up-triangle')).toBe(0);
    expect(count(root, '.glyphicon-Down-triangle')).toBe(1);
    expect(count(root, '.glyphicon-Unknown-triangle')).toBe(0);
    expect(count(root, 'span.dead')).toBe(2);
    expect(root.querySelectorAll('span.dead')[1]).toHaveTextContent('0%');
  });

  it('displays only unknown count when only unknown count is provided', () => {
    const root = renderCounts({ unknown: 2 } as IInstanceCounts);
    expect(count(root, 'span.counter')).toBe(1);
    expect(count(root, '.glyphicon-Up-triangle')).toBe(0);
    expect(count(root, '.glyphicon-Down-triangle')).toBe(0);
    expect(count(root, '.glyphicon-Unknown-triangle')).toBe(1);
    expect(count(root, 'span.unknown')).toBe(1);
    expect(count(root, 'span.dead')).toBe(0);
  });

  it('displays only unknown count when only starting count is provided', () => {
    const root = renderCounts({ starting: 2 } as IInstanceCounts);
    expect(count(root, 'span.counter')).toBe(1);
    expect(count(root, '.glyphicon-Up-triangle')).toBe(0);
    expect(count(root, '.glyphicon-Down-triangle')).toBe(0);
    expect(count(root, '.glyphicon-Unknown-triangle')).toBe(1);
    expect(count(root, 'span.unknown')).toBe(1);
    expect(count(root, 'span.dead')).toBe(1);
  });

  it('displays only total unknown count when only starting and unknown counts are provided', () => {
    const root = renderCounts({ starting: 1, unknown: 1 } as IInstanceCounts);
    expect(count(root, 'span.counter')).toBe(1);
    expect(count(root, '.glyphicon-Up-triangle')).toBe(0);
    expect(count(root, '.glyphicon-Down-triangle')).toBe(0);
    expect(count(root, '.glyphicon-Unknown-triangle')).toBe(1);
    expect(count(root, 'span.unknown')).toBe(1);
    expect(count(root, 'span.dead')).toBe(1);
  });

  it('displays only outOfService minus when only outOfService count is provided', () => {
    const root = renderCounts({ outOfService: 2 } as IInstanceCounts);
    expect(count(root, 'span.counter')).toBe(1);
    expect(count(root, '.glyphicon-Up-triangle')).toBe(0);
    expect(count(root, '.glyphicon-Down-triangle')).toBe(0);
    expect(count(root, '.glyphicon-minus')).toBe(1);
    expect(count(root, 'span.dead')).toBe(0);
  });

  it('displays up and outOfService when up and outOfService counts are provided', () => {
    const root = renderCounts({ up: 2, outOfService: 2 } as IInstanceCounts);
    expect(count(root, 'span.counter')).toBe(1);
    expect(count(root, '.glyphicon-Up-triangle')).toBe(1);
    expect(count(root, '.glyphicon-Down-triangle')).toBe(0);
    expect(count(root, '.glyphicon-OutOfService-triangle')).toBe(1);
    expect(count(root, 'span.healthy')).toBe(2);
    expect(root.querySelectorAll('span.healthy')[1]).toHaveTextContent('100%');
  });

  it('displays only succeeded count when only succeeded count is provided', () => {
    const root = renderCounts({ succeeded: 2 } as IInstanceCounts);
    expect(count(root, 'span.counter')).toBe(1);
    expect(count(root, '.glyphicon-Up-triangle')).toBe(0);
    expect(count(root, '.glyphicon-Down-triangle')).toBe(0);
    expect(count(root, '.glyphicon-Succeeded-triangle')).toBe(1);
    expect(count(root, 'span.healthy')).toBe(1);
  });

  it('displays only failed count when only failed count is provided', () => {
    const root = renderCounts({ failed: 2 } as IInstanceCounts);
    expect(count(root, 'span.counter')).toBe(1);
    expect(count(root, '.glyphicon-Up-triangle')).toBe(0);
    expect(count(root, '.glyphicon-Down-triangle')).toBe(0);
    expect(count(root, '.glyphicon-Failed-triangle')).toBe(1);
    expect(count(root, 'span.dead')).toBe(1);
  });

  it('displays up and down counts when up and down counts are provided', () => {
    const root = renderCounts({ up: 2, down: 2 } as IInstanceCounts);
    expect(count(root, 'span.counter')).toBe(1);
    expect(count(root, '.glyphicon-Up-triangle')).toBe(1);
    expect(count(root, '.glyphicon-Down-triangle')).toBe(1);
    expect(count(root, '.glyphicon-OutOfService-triangle')).toBe(0);
    expect(count(root, 'span.dead')).toBe(1);
    expect(root.querySelector('span.unhealthy')).toHaveTextContent('50%');
  });

  it('displays up and unknown counts when up and unknown counts are provided', () => {
    const root = renderCounts({ up: 2, unknown: 2 } as IInstanceCounts);
    expect(count(root, 'span.counter')).toBe(1);
    expect(count(root, '.glyphicon-Up-triangle')).toBe(1);
    expect(count(root, '.glyphicon-Down-triangle')).toBe(0);
    expect(count(root, '.glyphicon-Unknown-triangle')).toBe(1);
    expect(count(root, '.glyphicon-OutOfService-triangle')).toBe(0);
    expect(count(root, 'span.unknown')).toBe(1);
    expect(root.querySelector('span.unhealthy')).toHaveTextContent('50%');
  });

  it('updates when counters change', () => {
    const rendered = render(<HealthCounts container={{ up: 2, down: 2 } as IInstanceCounts} />);
    expect(rendered.container.querySelector('span.unhealthy')).toHaveTextContent('50%');
    rendered.rerender(<HealthCounts container={{ up: 3, down: 1 } as IInstanceCounts} />);
    expect(rendered.container.querySelector('span.unhealthy')).toHaveTextContent('75%');
    rendered.rerender(<HealthCounts container={{ up: 4, down: 0 } as IInstanceCounts} />);
    expect(rendered.container.querySelectorAll('span.healthy')[1]).toHaveTextContent('100%');
    expect(count(rendered.container, '.glyphicon-Down-triangle')).toBe(0);
  });
});
