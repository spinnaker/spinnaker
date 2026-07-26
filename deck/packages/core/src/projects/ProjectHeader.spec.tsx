import { mount } from 'enzyme';
import React from 'react';

import { ProjectHeader } from './ProjectHeader';
import { wrapWithRouter } from '../utils/testUtils';

describe('<ProjectHeader />', () => {
  const transition = {
    router: {
      globals: {
        success$: {
          pipe: () => ({
            subscribe: (callback: any) => {
              callback({
                to: () => ({ name: 'home.project.dashboard' }),
                params: () => ({}),
              });
              return { unsubscribe: () => null };
            },
          }),
        },
      },
      stateService: {},
    },
  } as any;

  it('renders the dashboard header in a direct React route', () => {
    const wrapper = mount(
      wrapWithRouter(
        <ProjectHeader
          projectConfiguration={
            {
              name: 'kubernetesproject',
              config: { applications: ['kubernetesapp'] },
            } as any
          }
          transition={transition}
        />,
      ),
    );

    expect(wrapper.find('.project-name').text()).toContain('kubernetesproject /');
    expect(wrapper.find('h2 .project-view .dropdown span.clickable').hostNodes().length).toBe(1);
    expect(wrapper.find('.configure-project-link').text()).toContain('Project Configuration');

    wrapper.unmount();
  });
});
