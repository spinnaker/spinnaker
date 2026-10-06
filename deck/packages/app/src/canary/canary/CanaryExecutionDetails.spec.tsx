import { screen, within } from '@testing-library/react';
import React from 'react';

import { timestamp } from '@spinnaker/core';

import { CanaryExecutionDetails } from './CanaryExecutionDetails';
import { renderWithRouter } from '../../../../core/src/utils/testUtils/rtl';

describe('CanaryExecutionDetails', () => {
  it('renders deployment last updated from the canary analysis result', () => {
    const lastUpdated = 1710000000000;
    const Component = CanaryExecutionDetails as React.ComponentType<any>;
    renderWithRouter(
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
    );

    const deploymentRow = screen.getAllByRole('row')[1];
    expect(within(deploymentRow).getAllByRole('cell')[4]).toHaveTextContent(timestamp(lastUpdated));
  });
});
