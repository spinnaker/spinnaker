import { render } from '@testing-library/react';
import { servicesPlugin, UIRouterContext, UIRouterReact } from '@uirouter/react';
import React from 'react';
import { RecoilRoot } from 'recoil';

import {
  mockLoadBalancerDataSourceConfig,
  mockPipelineDataSourceConfig,
  mockServerGroupDataSourceConfig,
} from '@spinnaker/mocks';

import { NavSection } from './NavSection';
import { ApplicationModelBuilder } from '../../application';

describe('NavItem', () => {
  it('should render multiple categories', () => {
    const app = ApplicationModelBuilder.createApplicationForTests(
      'testapp',
      mockPipelineDataSourceConfig,
      mockLoadBalancerDataSourceConfig,
      mockServerGroupDataSourceConfig,
    );

    app.dataSources.forEach((dataSource, index) => {
      dataSource.sref = `route${index}`;
      dataSource.activeState = `route${index}`;
      dataSource.badge = null;
    });
    const router = new UIRouterReact();
    router.plugin(servicesPlugin);
    app.dataSources.forEach((_dataSource, index) => router.stateRegistry.register({ name: `route${index}` }));
    const { container } = render(
      <RecoilRoot>
        <UIRouterContext.Provider value={router}>
          <NavSection app={app} dataSources={app.dataSources} />
        </UIRouterContext.Provider>
      </RecoilRoot>,
    );
    expect(container.querySelectorAll('.nav-category')).toHaveLength(3);
    router.dispose();
  });

  it('should not render if no dataSources', () => {
    const app = ApplicationModelBuilder.createApplicationForTests('testapp');

    const { container } = render(
      <RecoilRoot>
        <NavSection app={app} dataSources={[]} />
      </RecoilRoot>,
    );
    expect(container.querySelector('.nav-section')).toBeEmptyDOMElement();
  });
});
