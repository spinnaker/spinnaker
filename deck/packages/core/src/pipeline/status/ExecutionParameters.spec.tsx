import { render, screen } from '@testing-library/react';
import React from 'react';

import type { IDisplayableParameter } from './ExecutionParameters';
import { ExecutionParameters } from './ExecutionParameters';

describe('<ExecutionParameters/>', () => {
  it(`show only pin params, but there's no pinnedDisplayableParameters should return null`, function () {
    const parameters: IDisplayableParameter[] = [{ key: '1', value: 'a' }];

    const { container } = render(
      <ExecutionParameters
        pinnedDisplayableParameters={[]}
        displayableParameters={parameters}
        shouldShowAllParams={false}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it(`show only pinned parameters in 2 columns format`, function () {
    const parameters: IDisplayableParameter[] = [
      { key: '1', value: 'a' },
      { key: '2', value: 'b' },
    ];

    const { container } = render(
      <ExecutionParameters
        pinnedDisplayableParameters={parameters}
        displayableParameters={[]}
        shouldShowAllParams={false}
      />,
    );

    expect(container.querySelectorAll('.execution-parameters-column')).toHaveLength(2);
    expect(screen.getByText('1:')).toBeVisible();
    expect(screen.getByText('a')).toBeVisible();
    expect(screen.getByText('2:')).toBeVisible();
    expect(screen.getByText('b')).toBeVisible();
  });

  it(`show all params, but there's no displayableParameters should return null`, function () {
    const parameters: IDisplayableParameter[] = [{ key: '1', value: 'a' }];

    const { container } = render(
      <ExecutionParameters
        pinnedDisplayableParameters={parameters}
        displayableParameters={[]}
        shouldShowAllParams={true}
      />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it(`show all parameters in 2 columns format`, function () {
    const parameters: IDisplayableParameter[] = [
      { key: '1', value: 'a' },
      { key: '2', value: 'b' },
    ];

    const { container } = render(
      <ExecutionParameters
        pinnedDisplayableParameters={[]}
        displayableParameters={parameters}
        shouldShowAllParams={true}
      />,
    );

    expect(screen.getByText('Parameters')).toBeVisible();
    expect(container.querySelectorAll('.execution-parameters-column')).toHaveLength(2);
    expect(screen.getByText('1:')).toBeVisible();
    expect(screen.getByText('a')).toBeVisible();
    expect(screen.getByText('2:')).toBeVisible();
    expect(screen.getByText('b')).toBeVisible();
  });
});
