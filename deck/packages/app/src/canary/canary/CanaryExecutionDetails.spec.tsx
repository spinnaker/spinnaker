import { shallow } from 'enzyme';
import React from 'react';

import { timestamp } from '@spinnaker/core';

import { CanaryExecutionDetails } from './CanaryExecutionDetails';

describe('CanaryExecutionDetails', () => {
  it('renders deployment last updated from the canary analysis result', () => {
    const lastUpdated = 1710000000000;
    const Component = CanaryExecutionDetails as React.ComponentType<any>;
    const component = shallow(
      <Component
        name="canarySummary"
        current="canarySummary"
        application={{}}
        execution={{}}
        stage={{
          context: {
            canary: {
              canaryDeployments: [
                {
                  canaryAnalysisResult: { lastUpdated },
                  canaryCluster: { region: 'us-west-1' },
                  canaryResult: { timeDuration: { durationString: '1 hour' } },
                },
              ],
            },
          },
          exceptions: [],
        }}
      />,
    )
      .find('CanarySummary')
      .dive();

    const deploymentRow = component.find('table').first().find('tbody tr').at(1);
    expect(deploymentRow.find('td').last().text()).toBe(timestamp(lastUpdated));
  });
});
