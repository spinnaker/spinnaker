import type { Mock } from 'vitest';
import { render, screen } from '@testing-library/react';
import { setupUser } from '../utils/testUtils/userEvent';
import React from 'react';

import type { IInsightMenuProps } from './InsightMenu';
import { InsightMenuComponent } from './InsightMenu';
import { CreateApplicationModal } from '../application/modal/CreateApplicationModal';
import type { CacheInitializerService } from '../cache/cacheInitializer.service';

describe('<InsightMenu />', () => {
  let go: Mock;

  beforeEach(() => (go = vi.fn()));

  function renderMenu(params: IInsightMenuProps) {
    // Set defaults to zero so we only need to pass in the prop we want rendered
    const mergedParams = { ...{ createApp: false, createProject: false, refreshCaches: false }, ...params };
    return render(
      <InsightMenuComponent
        createApp={mergedParams.createApp}
        createProject={mergedParams.createProject}
        refreshCaches={mergedParams.refreshCaches}
        deckRuntimeServices={
          { cacheInitializer: {} as CacheInitializerService } as React.ComponentProps<
            typeof InsightMenuComponent
          >['deckRuntimeServices']
        }
        router={{} as React.ComponentProps<typeof InsightMenuComponent>['router']}
        stateParams={{}}
        stateService={{ go } as React.ComponentProps<typeof InsightMenuComponent>['stateService']}
      />,
    );
  }

  it('should only render create application button when initialized', () => {
    renderMenu({ createApp: true });
    const buttons = screen.getAllByRole('link');

    expect(buttons).toHaveLength(1);
    expect(buttons[0]).toHaveTextContent('Create Application');
    // Button should always be primary for create application
    // FIXME: when this project moves to v1+ of react-bootstrap this prop will need to change.
    expect(buttons[0]).toHaveClass('btn-primary');
  });

  it('should only render create project button when initialized', () => {
    renderMenu({ createProject: true });
    const buttons = screen.getAllByRole('link');

    expect(buttons).toHaveLength(1);
    expect(buttons[0]).toHaveTextContent('Create Project');
    // If project is the only button rendered, it should be primary.
    // FIXME: when this project moves to v1+ of react-bootstrap this prop will need to change.
    expect(buttons[0]).toHaveClass('btn-primary');
  });

  it('should only render refresh cache button when initialized', () => {
    // note: this test doesn't validate the state changes that could occur w/
    //       the refresh button in particular.
    renderMenu({ refreshCaches: true });

    expect(screen.getAllByRole('link')).toHaveLength(1);
    expect(screen.getByRole('link')).toHaveTextContent('Refresh');
  });

  it('should only render create application as primary when multiple buttons are rendered', () => {
    renderMenu({ createApp: true, createProject: true });
    const buttons = screen.getAllByRole('link');

    expect(buttons).toHaveLength(2);
    // Project button should be first
    expect(buttons[0]).toHaveTextContent('Create Project');
    // FIXME: when this project moves to v1+ of react-bootstrap this prop will need to change.
    expect(buttons[0]).toHaveClass('btn-default');
    // Application button should be second, so that it renders furthest to the right
    expect(buttons[1]).toHaveTextContent('Create Application');
    // FIXME: when this project moves to v1+ of react-bootstrap this prop will need to change.
    expect(buttons[1]).toHaveClass('btn-primary');
  });

  it('opens the direct application modal and routes after creation', async () => {
    const user = setupUser();
    vi.spyOn(CreateApplicationModal, 'show').mockResolvedValue({ name: 'myapp' });
    renderMenu({ createApp: true });

    await user.click(screen.getByRole('link', { name: 'Create Application' }));

    expect(CreateApplicationModal.show).toHaveBeenCalledWith();
    expect(go).toHaveBeenCalledWith('home.applications.application', { application: 'myapp' });
  });
});
