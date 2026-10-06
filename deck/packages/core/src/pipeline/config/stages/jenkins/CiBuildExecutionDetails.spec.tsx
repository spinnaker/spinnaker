import { render, screen } from '@testing-library/react';
import React from 'react';

import { CiBuildExecutionDetails } from './CiBuildExecutionDetails';

describe('<CiBuildExecutionDetails />', () => {
  it('renders test result URL names as human-readable text', () => {
    render(
      <CiBuildExecutionDetails
        name="jenkinsConfig"
        current="jenkinsConfig"
        buildServiceLabel="Controller"
        title="Jenkins Stage Configuration"
        stage={{
          context: {
            master: 'master',
            job: 'job',
            buildInfo: {
              url: 'https://build.example/',
              number: 1,
              testResults: [{ urlName: 'some_test_name', totalCount: 3, failCount: 1, skipCount: 0 }],
            },
          },
        }}
      />,
    );

    expect(screen.getByRole('link', { name: 'Some Test Name' })).toHaveAttribute(
      'href',
      'https://build.example/some_test_name',
    );
  });
});
