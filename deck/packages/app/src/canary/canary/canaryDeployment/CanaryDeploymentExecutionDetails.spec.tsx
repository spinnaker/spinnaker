import { render, screen } from '@testing-library/react';
import React from 'react';

import { UrlBuilder } from '@spinnaker/core';

import { CanaryDeploymentExecutionDetailsComponent } from './CanaryDeploymentExecutionDetails';

describe('CanaryDeploymentExecutionDetails', () => {
  it('passes the injected project to deployment cluster links', () => {
    const buildUrl = vi.spyOn(UrlBuilder, 'buildFromMetadata').mockReturnValue('/cluster');
    render(
      <CanaryDeploymentExecutionDetailsComponent
        {...({ router: {}, stateParams: { project: 'test-project' }, stateService: {} } as any)}
        current="deploymentDetails"
        name="deploymentDetails"
        stage={
          {
            context: {
              application: 'test-app',
              baselineCluster: { name: 'baseline', accountName: 'test-account' },
            },
          } as any
        }
      />,
    );

    expect(screen.getByRole('link', { name: 'baseline' })).toHaveAttribute('href', '/cluster');
    expect(buildUrl).toHaveBeenCalledWith(expect.objectContaining({ project: 'test-project' }));
  });
});
