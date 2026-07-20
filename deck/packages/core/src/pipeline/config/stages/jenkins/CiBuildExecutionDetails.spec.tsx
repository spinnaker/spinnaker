import { mount } from 'enzyme';
import React from 'react';

import { CiBuildExecutionDetails } from './CiBuildExecutionDetails';

describe('<CiBuildExecutionDetails />', () => {
  it('renders test result URL names as human-readable text', () => {
    const component = mount(
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

    const testResultLink = component.find('a[href="https://build.example/some_test_name"]');
    expect(testResultLink.text().trim()).toBe('Some Test Name');
  });
});
