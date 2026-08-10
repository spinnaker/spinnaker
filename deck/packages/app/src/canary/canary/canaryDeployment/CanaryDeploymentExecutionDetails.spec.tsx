import { shallow } from 'enzyme';
import React from 'react';

import { CanaryDeploymentExecutionDetailsComponent } from './CanaryDeploymentExecutionDetails';

describe('CanaryDeploymentExecutionDetails', () => {
  it('passes the injected project to deployment cluster links', () => {
    const component = shallow(
      <CanaryDeploymentExecutionDetailsComponent
        {...({ router: {}, stateParams: { project: 'test-project' }, stateService: {} } as any)}
        current={true}
        name="deploymentDetails"
        stage={{ context: {} } as any}
      />,
    );

    expect(component.find('DeploymentDetails').prop('project')).toBe('test-project');
  });
});
