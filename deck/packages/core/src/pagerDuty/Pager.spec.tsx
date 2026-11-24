import { fireEvent, screen, waitFor } from '@testing-library/react';
import { setupUser } from '../utils/testUtils/userEvent';
import React from 'react';
import { of } from 'rxjs';

import { PagerComponent } from './Pager';
import type { IRouterInjectedProps } from '../navigation/routerContext';
import { ApplicationReader } from '../application';
import type { IApplicationSummary } from '../application';
import { PagerDutyReader } from './pagerDuty.read.service';
import type { IPagerDutyService } from './pagerDuty.read.service';
import { renderWithRouter } from '../utils/testUtils/rtl';

describe('Pager', () => {
  const service: IPagerDutyService = {
    id: 'service-id',
    integration_key: 'service-key',
    name: 'Injected Query Service',
    policy: 'policy-id',
    status: 'active',
    lastIncidentTimestamp: '2026-07-31T12:00:00Z',
  } as IPagerDutyService;
  const application = { name: 'test-app', pdApiKey: 'service-key' } as IApplicationSummary;

  beforeEach(() => {
    vi.spyOn(ApplicationReader, 'listApplications').mockResolvedValue([application]);
    vi.spyOn(PagerDutyReader, 'listOnCalls').mockReturnValue(of({}));
    vi.spyOn(PagerDutyReader, 'listServices').mockReturnValue(of([service]));
  });

  const renderPager = (stateParams: IRouterInjectedProps['stateParams'] = {}) => {
    const go = vi.fn();
    renderWithRouter(
      <PagerComponent
        router={{} as IRouterInjectedProps['router']}
        stateParams={stateParams}
        stateService={{ go } as IRouterInjectedProps['stateService']}
      />,
    );
    return go;
  };

  const closeAutomaticPageModal = async (user: ReturnType<typeof setupUser>) => {
    await user.click(await screen.findByRole('button', { name: 'Close' }));
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Page 1 service' })).not.toBeInTheDocument());
  };

  it('initializes filters from injected route params', async () => {
    renderPager({
      app: 'test-app',
      by: 'last',
      direction: 'DESC',
      hideNoApps: true,
      keys: ['service-key'],
      q: 'injected query',
    });

    expect(screen.getByPlaceholderText('Service, application, name')).toHaveValue('injected query');
    expect(screen.getByRole('checkbox', { name: 'Hide services with no associated apps' })).toBeChecked();
    expect(await screen.findByRole('heading', { name: 'Page 1 service' })).toBeInTheDocument();
    expect(screen.getAllByText('Injected Query Service').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('1 policy selected')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Last Incident', hidden: true })).toHaveAttribute(
      'aria-sort',
      'descending',
    );
  });

  it('updates sorting through the injected state service', async () => {
    const go = renderPager({ app: 'test-app' });
    await screen.findByRole('link', { name: 'Injected Query Service' });
    go.mockClear();

    fireEvent.click(screen.getByText('Last Incident'));
    fireEvent.click(screen.getByText('Last Incident'));

    expect(go).toHaveBeenLastCalledWith('.', { by: 'last', direction: 'DESC' });
  });

  it('updates selected service keys through the injected state service', async () => {
    const user = setupUser();
    const go = renderPager({ app: 'test-app' });
    await closeAutomaticPageModal(user);
    const serviceLink = screen.getByRole('link', { name: 'Injected Query Service' });
    go.mockClear();

    const row = serviceLink.closest('[role="row"]') as HTMLElement;
    fireEvent.click(row);
    await waitFor(() => expect(screen.getByText('0 policies selected')).toBeInTheDocument());
    fireEvent.click(
      screen.getByRole('link', { name: 'Injected Query Service' }).closest('[role="row"]') as HTMLElement,
    );

    await waitFor(() => expect(go).toHaveBeenLastCalledWith('.', { keys: ['service-key'] }));
    expect(screen.getByText('1 policy selected')).toBeInTheDocument();
  });

  it('updates application visibility through the injected state service', async () => {
    const user = setupUser();
    const go = renderPager({ app: 'test-app' });
    await closeAutomaticPageModal(user);
    go.mockClear();

    await user.click(screen.getByRole('checkbox', { name: 'Hide services with no associated apps' }));

    await waitFor(() => expect(go).toHaveBeenCalledWith('.', { hideNoApps: true }));
  });
});
