import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';
import type { Mock } from 'vitest';

import {
  ApplicationAttributesForm,
  ApplicationConfigComponent,
  ApplicationDataSourceEditor,
} from './ApplicationConfig';
import { AccountService } from '../../account';
import { RequestBuilder } from '../../api';
import { AuthenticationService } from '../../authentication';
import { ClusterMatcher } from '../../cluster';
import { SETTINGS } from '../../config/settings';
import { AppNotificationsService } from '../../notification/AppNotificationsService';
import { ReactModal } from '../../presentation';
import { Registry } from '../../registry';
import { ApplicationWriter } from '../service/ApplicationWriter';
import { TaskReader } from '../../task';
import { renderWithRouter } from '../../utils/testUtils/rtl';

const routerProps = { stateService: { go: () => undefined } as any };

describe('<ApplicationConfig />', () => {
  let originalFeatures: typeof SETTINGS.feature;
  let originalGitSources: typeof SETTINGS.gitSources;
  let originalSlack: typeof SETTINGS.slack;

  beforeEach(() => {
    originalFeatures = SETTINGS.feature;
    originalGitSources = SETTINGS.gitSources;
    originalSlack = SETTINGS.slack;
    SETTINGS.feature = {
      ...SETTINGS.feature,
      chaosMonkey: true,
      fiatEnabled: false,
      managedResources: true,
      pagerDuty: true,
      snapshots: true,
      slack: true,
    };
    SETTINGS.slack = { baseUrl: 'https://slack.example.com' } as any;
    vi.spyOn(AccountService, 'listProviders').mockResolvedValue([] as any);
    vi.spyOn(AccountService, 'listAllAccounts').mockResolvedValue([] as any);
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockResolvedValue({} as any);
    vi.spyOn(RequestBuilder.defaultHttpClient, 'get').mockResolvedValue([] as any);
    vi.spyOn(RequestBuilder.defaultHttpClient, 'post').mockResolvedValue({
      data: { application: { isPaused: false } },
    } as any);
  });

  afterEach(() => {
    SETTINGS.feature = originalFeatures;
    SETTINGS.gitSources = originalGitSources;
    SETTINGS.slack = originalSlack;
  });

  const renderConfig = (application = buildApplication()) =>
    renderWithRouter(<ApplicationConfigComponent {...routerProps} app={application as any} />);

  const renderAttributesForm = (application = buildApplication(), onAttributesSaved = vi.fn()) =>
    render(
      <ApplicationAttributesForm
        application={application as any}
        isConfigured={true}
        onAttributesSaved={onAttributesSaved}
      />,
    );

  const formControl = (
    root: HTMLElement,
    label: string,
  ): HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement => {
    const labelNode = within(root).getByText(label, { exact: true });
    const control = labelNode.closest('.form-group')?.querySelector('input, textarea, select');
    if (
      !(
        control instanceof HTMLInputElement ||
        control instanceof HTMLTextAreaElement ||
        control instanceof HTMLSelectElement
      )
    ) {
      throw new Error(`No form control found for ${label}`);
    }
    return control;
  };

  const section = (container: HTMLElement, pageKey: string) =>
    container.querySelector(`[data-page-content="${pageKey}"]`) as HTMLElement;

  it('renders application config sections', async () => {
    const { container } = renderConfig();
    const expected = [
      'Application Attributes',
      'Managed Resources',
      'Notifications',
      'Features',
      'Links',
      'Chaos Monkey',
      'Traffic Guards',
      'Serialize Application',
      'Custom Banners',
      'Default Filters',
      'Delete Application',
    ];

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Managed Resources' })).toBeInTheDocument());
    expect(Array.from(container.querySelectorAll('.sticky-header')).map((node) => node.textContent)).toEqual(expected);
    expect(container.querySelectorAll('[data-page-content]')).toHaveLength(11);
  });

  it('redirects missing applications using the injected state service', () => {
    const stateService = { go: vi.fn() };
    renderWithRouter(
      <ApplicationConfigComponent
        app={buildApplication({ notFound: true }) as any}
        stateService={stateService as any}
      />,
    );
    expect(stateService.go).toHaveBeenCalledWith('home.infrastructure', null, { location: 'replace' });
  });

  it('loads, filters, and updates application-level notifications through the real notification list', async () => {
    const initialNotification = {
      address: 'initial@example.com',
      level: 'application',
      type: 'email',
      when: ['pipeline.failed'],
    };
    const loadedNotification = {
      address: 'loaded@example.com',
      level: 'application',
      type: 'email',
      when: ['pipeline.complete'],
    };
    const notifications = deferred<any>();
    vi.spyOn(Registry.pipeline, 'getNotificationTypes').mockReturnValue([{ key: 'email' }] as any);
    vi.spyOn(AppNotificationsService, 'getNotificationsForApplication').mockReturnValue(notifications.promise);
    renderConfig(buildApplication({ attributes: { notifications: [initialNotification] } }));

    expect(screen.getByText('You can edit notification settings for this application')).toBeInTheDocument();
    expect(screen.getByText('initial@example.com')).toBeInTheDocument();
    expect(screen.getByText('Any pipeline has failed')).toBeInTheDocument();
    expect(AppNotificationsService.getNotificationsForApplication).toHaveBeenCalledWith('fnord');

    notifications.resolve({
      application: 'fnord',
      email: [loadedNotification, { ...loadedNotification, address: 'stage@example.com', level: 'stage' }],
    });
    await waitFor(() => expect(screen.getByText('loaded@example.com')).toBeInTheDocument());
    expect(screen.queryByText('initial@example.com')).not.toBeInTheDocument();
    expect(screen.queryByText('stage@example.com')).not.toBeInTheDocument();
    expect(screen.getByText('Any pipeline is complete')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));
    expect(screen.queryByText('loaded@example.com')).not.toBeInTheDocument();
  });

  it('renders configured application attributes with an edit button', () => {
    renderConfig(buildApplication({ attributes: { appGroup: 'payments', aliases: 'pay', email: 'user@example.com' } }));

    expect(screen.getByText('Owner')).toBeInTheDocument();
    expect(screen.getByText('user@example.com')).toBeInTheDocument();
    expect(screen.getByText('App Group')).toBeInTheDocument();
    expect(screen.getByText('payments')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /edit application attributes/i })).toBeInTheDocument();
  });

  it('renders only application attributes before the application is configured', () => {
    const { container } = renderConfig(buildApplication({ attributes: { email: null } }));

    expect(container.querySelectorAll('.page-section')).toHaveLength(1);
    expect(screen.getByText('This application has not been configured.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create application/i })).toBeInTheDocument();
  });

  it('opens application attributes in a modal instead of editing inline', () => {
    const application = buildApplication();
    vi.spyOn(ReactModal, 'show').mockResolvedValue(application.attributes);
    renderConfig(application);

    fireEvent.click(screen.getByRole('button', { name: /edit application attributes/i }));

    expect(ReactModal.show).toHaveBeenCalledWith(
      ApplicationAttributesForm,
      expect.objectContaining({ application, isConfigured: true }),
      expect.objectContaining({ dialogClassName: 'modal-lg' }),
    );
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
  });

  it('renders configured sections after application attributes are saved', async () => {
    const application = buildApplication({ attributes: { email: null } });
    vi.spyOn(ReactModal, 'show').mockImplementation((_component, props: any) => {
      props.onAttributesSaved({ ...application.attributes, email: 'user@example.com' });
      return Promise.resolve(application.attributes) as any;
    });
    renderConfig(application);

    fireEvent.click(screen.getByRole('button', { name: /create application/i }));

    expect(await screen.findByRole('heading', { name: 'Notifications' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Delete Application' })).toBeInTheDocument();
  });

  it('does not render accounts as an editable application attributes field', () => {
    const { container } = renderAttributesForm();

    expect(within(container).queryByRole('textbox', { name: /accounts/i })).not.toBeInTheDocument();
    expect(screen.getByText('Accounts are managed by Front50 and cannot be changed here.')).toBeInTheDocument();
  });

  it('renders the application name as read-only in the attributes modal', () => {
    const { container } = renderAttributesForm();

    expect(container.querySelector('.form-control-static')).toHaveTextContent('fnord');
    expect(within(container).queryByRole('textbox', { name: /^name$/i })).not.toBeInTheDocument();
  });

  it('renders source repo type as a selector and only shows repo fields after a type is selected', () => {
    SETTINGS.gitSources = ['github', 'gitlab'];
    const { container } = renderAttributesForm(
      buildApplication({ attributes: { repoType: '', repoProjectKey: '', repoSlug: '' } }),
    );
    const repoType = container.querySelector('select[name="repoType"]') as HTMLSelectElement;

    expect(Array.from(repoType.options).map(({ value }) => value)).toEqual(['', 'github', 'gitlab']);
    expect(screen.queryByText('Repo Project')).not.toBeInTheDocument();
    expect(screen.queryByText('Repo Name')).not.toBeInTheDocument();
    fireEvent.change(repoType, { target: { value: 'github' } });
    expect(screen.getByText('Repo Project')).toBeInTheDocument();
    expect(screen.getByText('Repo Name')).toBeInTheDocument();
  });

  it('renders application attribute help fields from the old edit modal', () => {
    SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled: true };
    const { container } = renderAttributesForm();

    expect(container.querySelectorAll('.help-field').length).toBeGreaterThanOrEqual(5);
    expect(screen.getByText('Instance Health')).toBeInTheDocument();
    expect(screen.getByText('Pipeline Behavior')).toBeInTheDocument();
    expect(screen.getByText('Permissions')).toBeInTheDocument();
  });

  it('renders cloud providers as a multi-select instead of checkboxes', async () => {
    vi.spyOn(AccountService, 'listProviders').mockResolvedValue(['aws', 'gce'] as any);
    const { container } = renderAttributesForm();

    await waitFor(() => expect(container.querySelector('input[name="cloudProviders"]')).toBeInTheDocument());
    expect(container.querySelector('.Select--multi')).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: /aws/i })).not.toBeInTheDocument();
  });

  it('renders application attribute modal group labels for health and pipeline options', () => {
    renderAttributesForm();
    expect(screen.getByText('Instance Health')).toBeInTheDocument();
    expect(screen.getByText('Pipeline Behavior')).toBeInTheDocument();
  });

  it('renders feature names in bold with descriptions aligned below the label text', () => {
    const application = buildApplication();
    application.dataSources = [
      {
        key: 'serverGroups',
        label: 'Server Groups',
        description: 'Shows server groups for this app.',
        visible: true,
        optional: true,
      },
    ];
    const { container } = render(<ApplicationDataSourceEditor application={application as any} />);

    expect(container.querySelector('strong.application-feature-name')).toHaveTextContent('Server Groups');
    expect(container.querySelector('.application-feature-description')).toHaveTextContent(
      'Shows server groups for this app.',
    );
  });

  it('edits permissions with the permissions configurer instead of raw JSON', () => {
    SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled: true };
    const { container } = renderAttributesForm(
      buildApplication({ attributes: { permissions: { READ: ['readers'], EXECUTE: [], WRITE: [] } } }),
    );

    expect(screen.queryByText('Permissions JSON')).not.toBeInTheDocument();
    expect(container.querySelectorAll('.permissions-row')).toHaveLength(1);
    expect(container.querySelector('.permissions-row')).toHaveTextContent('readers');
  });

  (['READ', 'WRITE', 'EXECUTE'] as const).forEach((permissionType) => {
    ([null, ''] as Array<string | null>).forEach((emptyGroup) => {
      it(`rejects ${
        emptyGroup === null ? 'null' : 'empty'
      } ${permissionType} permission groups when fiat is enabled`, () => {
        SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled: true };
        const permissions = {
          READ: ['readers'],
          WRITE: ['writers'],
          EXECUTE: ['executors'],
          [permissionType]: [emptyGroup],
        };
        vi.spyOn(ApplicationWriter, 'updateApplication').mockReturnValue(new Promise(() => {}) as any);
        const { container } = renderAttributesForm(buildApplication({ attributes: { permissions } }));

        fireEvent.submit(container.querySelector('form')!);

        expect(screen.getByText('Permission groups cannot be empty.')).toBeInTheDocument();
        expect(ApplicationWriter.updateApplication).not.toHaveBeenCalled();
      });
    });
  });

  it('rejects read permissions without write permissions when fiat is enabled', () => {
    SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled: true };
    vi.spyOn(ApplicationWriter, 'updateApplication').mockReturnValue(new Promise(() => {}) as any);
    const { container } = renderAttributesForm(
      buildApplication({ attributes: { permissions: { READ: ['readers'], WRITE: [], EXECUTE: [] } } }),
    );

    fireEvent.submit(container.querySelector('form')!);

    expect(screen.getByText('Write permission is required when read permission is configured.')).toBeInTheDocument();
    expect(ApplicationWriter.updateApplication).not.toHaveBeenCalled();
  });

  it('allows hidden invalid legacy permissions when fiat is disabled', () => {
    vi.spyOn(ApplicationWriter, 'updateApplication').mockReturnValue(new Promise(() => {}) as any);
    const { container } = renderAttributesForm(
      buildApplication({ attributes: { permissions: { READ: ['readers', ''], WRITE: [], EXECUTE: [null] } } }),
    );

    fireEvent.submit(container.querySelector('form')!);

    expect(container.querySelector('.error-message')).not.toBeInTheDocument();
    expect(ApplicationWriter.updateApplication).toHaveBeenCalled();
  });

  it('allows saving permissions that warn about locking out the current user', () => {
    SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled: true };
    vi.spyOn(AuthenticationService, 'getAuthenticatedUser').mockReturnValue({ roles: ['current-user-group'] } as any);
    vi.spyOn(ApplicationWriter, 'updateApplication').mockReturnValue(new Promise(() => {}) as any);
    const { container } = renderAttributesForm(
      buildApplication({
        attributes: { permissions: { READ: ['other-group'], WRITE: ['other-group'], EXECUTE: ['other-group'] } },
      }),
    );

    expect(screen.getByText(/will lock you out/i)).toBeInTheDocument();
    fireEvent.submit(container.querySelector('form')!);
    expect(container.querySelector('.error-message')).not.toBeInTheDocument();
    expect(ApplicationWriter.updateApplication).toHaveBeenCalled();
  });

  it('preserves batched application attribute updates when saving', async () => {
    vi.spyOn(ApplicationWriter, 'updateApplication').mockResolvedValue({ id: '1' } as any);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue({} as any);
    const { container } = renderAttributesForm(
      buildApplication({ attributes: { appGroup: '', email: 'old@example.com' } }),
    );

    fireEvent.change(formControl(container, 'Owner Email'), { target: { value: 'new@example.com' } });
    fireEvent.change(formControl(container, 'App Group'), { target: { value: 'payments' } });
    fireEvent.submit(container.querySelector('form')!);

    await waitFor(() =>
      expect(ApplicationWriter.updateApplication).toHaveBeenCalledWith(
        expect.objectContaining({ appGroup: 'payments', email: 'new@example.com' }),
      ),
    );
    expect(TaskReader.waitUntilTaskCompletes).toHaveBeenCalled();
  });

  it('rejects fractional instance ports', () => {
    const onAttributesSaved = vi.fn();
    const { container } = renderAttributesForm(buildApplication(), onAttributesSaved);

    fireEvent.change(formControl(container, 'Instance Port'), { target: { value: '80.5' } });
    fireEvent.submit(container.querySelector('form')!);

    expect(screen.getByText('Instance port must be a whole number between 0 and 65535.')).toBeInTheDocument();
    expect(onAttributesSaved).not.toHaveBeenCalled();
  });

  it('clears saving state after saving attributes without a modal close handler', async () => {
    const onAttributesSaved = vi.fn();
    vi.spyOn(ApplicationWriter, 'updateApplication').mockResolvedValue({ id: '1' } as any);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue({} as any);
    const { container } = renderAttributesForm(buildApplication(), onAttributesSaved);

    fireEvent.submit(container.querySelector('form')!);
    expect(screen.getByRole('button', { name: 'Saving...' })).toBeDisabled();
    await waitFor(() => expect(onAttributesSaved).toHaveBeenCalled());
    expect(screen.getByRole('button', { name: 'Save Changes' })).toBeEnabled();
  });

  it('renders Slack and PagerDuty application metadata when present', () => {
    renderConfig(
      buildApplication({
        attributes: { pdApiKey: 'pager-duty-service', slackChannel: { id: 'C1234', name: 'deployments' } },
      }),
    );

    expect(screen.getByText('Pager Duty')).toBeInTheDocument();
    expect(screen.getByText('pager-duty-service')).toBeInTheDocument();
    expect(screen.getByText('Slack Channel')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '#deployments' })).toHaveAttribute(
      'href',
      'https://slack.example.com/app_redirect?channel=C1234',
    );
  });

  it('hides permissions when fiat is disabled', () => {
    renderConfig(buildApplication({ attributes: { permissions: { READ: ['readers'] } } }));
    expect(screen.queryByText('Permissions')).not.toBeInTheDocument();
    expect(screen.queryByText(/readers \(read\)/i)).not.toBeInTheDocument();
  });

  it('renders permissions when fiat is enabled', () => {
    SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled: true };
    renderConfig(buildApplication({ attributes: { permissions: { READ: ['readers'] } } }));
    expect(screen.getByText('Permissions')).toBeInTheDocument();
    expect(screen.getByText('readers (read)')).toBeInTheDocument();
  });

  it('renders functional React sections instead of migration blockers', () => {
    const { container } = renderConfig();
    expect(container).not.toHaveTextContent('still backed by');
    expect(section(container, 'links')).toBeInTheDocument();
    expect(section(container, 'chaos')).toBeInTheDocument();
    expect(section(container, 'traffic-guards')).toBeInTheDocument();
    expect(section(container, 'snapshot')).toBeInTheDocument();
  });

  it('loads region-capable accounts and preserves unknown persisted Chaos Monkey exception values', async () => {
    const application = buildApplication({
      attributes: {
        chaosMonkey: { exceptions: [{ account: 'legacy', region: 'moon-1', stack: 'payments', detail: 'api' }] },
      },
    });
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockResolvedValue({
      aws: { name: 'aws', regions: [{ name: 'eu-west-1' }] },
      kubernetes: { name: 'kubernetes', namespaces: ['default'] },
    } as any);
    const { container } = renderConfig(application);
    const chaos = section(container, 'chaos');

    await waitFor(() => expect(chaos.querySelector('select[name="chaosExceptionAccount"]')).toBeInTheDocument());
    expect(chaos.querySelector('input[placeholder="account"]')).not.toBeInTheDocument();
    expect(
      Array.from((chaos.querySelector('select[name="chaosExceptionAccount"]') as HTMLSelectElement).options).map(
        (o) => o.value,
      ),
    ).toEqual(['', 'aws', 'legacy']);
    expect(
      Array.from((chaos.querySelector('select[name="chaosExceptionRegion"]') as HTMLSelectElement).options).map(
        (o) => o.value,
      ),
    ).toEqual(['*', 'moon-1']);
  });

  it('resets the Chaos Monkey exception region when its account changes and keeps stack and detail editable', async () => {
    const application = buildApplication({
      attributes: {
        chaosMonkey: { exceptions: [{ account: 'legacy', region: 'moon-1', stack: 'payments', detail: 'api' }] },
      },
    });
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockResolvedValue({
      aws: { name: 'aws', regions: [{ name: 'eu-west-1' }] },
    } as any);
    const { container } = renderConfig(application);
    const chaos = section(container, 'chaos');
    const account = await waitFor(
      () => chaos.querySelector('select[name="chaosExceptionAccount"]') as HTMLSelectElement,
    );

    fireEvent.change(account, { target: { value: 'aws' } });
    fireEvent.change(chaos.querySelector('input[name="chaosExceptionStack"]')!, { target: { value: 'platform' } });
    fireEvent.change(chaos.querySelector('input[name="chaosExceptionDetail"]')!, { target: { value: 'worker' } });

    expect(chaos.querySelector('select[name="chaosExceptionRegion"]')).toHaveValue('*');
    expect(chaos.querySelector('input[name="chaosExceptionStack"]')).toHaveValue('platform');
    expect(chaos.querySelector('input[name="chaosExceptionDetail"]')).toHaveValue('worker');
  });

  it('waits for server groups before rendering sorted cluster matches for every Chaos Monkey exception', async () => {
    const ready = deferred<void>();
    const application = buildApplication({
      attributes: {
        chaosMonkey: {
          exceptions: [
            { account: 'prod', region: '*', stack: 'payments', detail: '*' },
            { account: 'prod', region: 'eu-west-1', stack: 'missing', detail: '*' },
          ],
        },
      },
    });
    application.clusters = [
      {
        account: 'prod',
        name: 'fnord-payments-zeta',
        serverGroups: [{ region: 'us-west-2' }, { region: 'us-east-1' }],
      },
      { account: 'prod', name: 'fnord-payments-alpha', serverGroups: [{ region: 'us-east-1' }] },
      { account: 'prod', name: 'fnord-other', serverGroups: [{ region: 'eu-west-1' }] },
    ];
    application.getDataSource = vi.fn((key: string) => ({
      ready: () => (key === 'managedResources' ? Promise.resolve({ hasManagedResources: true }) : ready.promise),
    }));
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockResolvedValue({
      prod: { name: 'prod', regions: [{ name: 'us-east-1' }] },
    } as any);
    vi.spyOn(ClusterMatcher, 'getMatchingRule');
    const { container } = renderConfig(application);
    const chaos = section(container, 'chaos');

    expect(ClusterMatcher.getMatchingRule).not.toHaveBeenCalled();
    ready.resolve();
    await waitFor(() => expect(chaos).toHaveTextContent('fnord-payments-alpha'));
    expect(chaos.textContent!.indexOf('fnord-payments-alpha')).toBeLessThan(
      chaos.textContent!.indexOf('fnord-payments-zeta'),
    );
    expect(chaos).toHaveTextContent('(no matches)');
  });

  (['credentials', 'server groups'] as const).forEach((failureSource) => {
    it(`shows unavailable matching when ${failureSource} fail to load`, async () => {
      const application = buildApplication({
        attributes: { chaosMonkey: { exceptions: [{ account: 'prod', region: '*', stack: 'payments', detail: '*' }] } },
      });
      const failure = new Error(`${failureSource} unavailable`);
      const credentials = vi.spyOn(AccountService, 'getCredentialsKeyedByAccount');
      if (failureSource === 'credentials') {
        credentials.mockRejectedValueOnce(failure).mockResolvedValue({} as any);
      } else {
        credentials.mockResolvedValue({ prod: { name: 'prod', regions: [{ name: 'eu-west-1' }] } } as any);
      }
      application.getDataSource = vi.fn((key: string) => ({
        ready: () =>
          key === 'managedResources'
            ? Promise.resolve({ hasManagedResources: true })
            : failureSource === 'server groups'
            ? Promise.reject(failure)
            : Promise.resolve(),
      }));
      const { container } = renderConfig(application);
      const chaos = section(container, 'chaos');

      await waitFor(() => expect(chaos).toHaveTextContent('(matches unavailable)'));
      expect(chaos.querySelector('.cluster-matches')).not.toBeInTheDocument();
    });
  });

  it('normalizes null and empty persisted Chaos Monkey regions for display, matching, and save', async () => {
    const application = buildApplication({
      attributes: {
        chaosMonkey: {
          exceptions: [
            { account: 'prod', region: null, stack: 'payments', detail: '*' },
            { account: 'prod', region: '', stack: 'platform', detail: '*' },
          ],
        },
      },
    });
    application.clusters = [
      { account: 'prod', name: 'fnord-payments', serverGroups: [{ region: 'eu-west-1' }] },
      { account: 'prod', name: 'fnord-platform', serverGroups: [{ region: 'us-east-1' }] },
    ];
    application.getDataSource = vi.fn((key: string) => ({
      ready: () => Promise.resolve(key === 'managedResources' ? { hasManagedResources: true } : undefined),
    }));
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockResolvedValue({
      prod: { name: 'prod', regions: [{ name: 'eu-west-1' }, { name: 'us-east-1' }] },
    } as any);
    vi.spyOn(ApplicationWriter, 'updateApplication').mockResolvedValue({ id: '1' } as any);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue({} as any);
    const { container } = renderConfig(application);
    const chaos = section(container, 'chaos');

    await waitFor(() => expect(chaos.querySelectorAll('select[name="chaosExceptionRegion"]')).toHaveLength(2));
    expect(
      Array.from(chaos.querySelectorAll('select[name="chaosExceptionRegion"]')).map(
        (select) => (select as HTMLSelectElement).value,
      ),
    ).toEqual(['*', '*']);
    fireEvent.click(within(chaos).getByRole('checkbox', { name: 'Enabled' }));
    fireEvent.click(within(chaos).getByRole('button', { name: /save changes/i }));
    await waitFor(() =>
      expect(ApplicationWriter.updateApplication).toHaveBeenCalledWith(
        expect.objectContaining({
          chaosMonkey: expect.objectContaining({
            exceptions: [
              { account: 'prod', region: '*', stack: 'payments', detail: '*' },
              { account: 'prod', region: '*', stack: 'platform', detail: '*' },
            ],
          }),
        }),
      ),
    );
  });

  it('preserves Chaos Monkey exception row identity when an earlier row is removed', async () => {
    const application = buildApplication({
      attributes: {
        chaosMonkey: {
          exceptions: [
            { account: 'prod', region: '*', stack: 'payments', detail: '*' },
            { account: 'prod', region: '*', stack: 'platform', detail: '*' },
          ],
        },
      },
    });
    application.getDataSource = vi.fn((key: string) => ({
      ready: () => Promise.resolve(key === 'managedResources' ? { hasManagedResources: true } : undefined),
    }));
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockResolvedValue({
      prod: { name: 'prod', regions: [{ name: 'eu-west-1' }] },
    } as any);
    const { container } = renderConfig(application);
    const chaos = section(container, 'chaos');
    await waitFor(() => expect(chaos.querySelectorAll('input[name="chaosExceptionStack"]')).toHaveLength(2));
    const secondStack = chaos.querySelectorAll('input[name="chaosExceptionStack"]')[1];
    fireEvent.change(secondStack, { target: { value: 'platform-edited' } });

    fireEvent.click(
      within(chaos.querySelectorAll('tbody tr')[0] as HTMLElement).getByRole('button', { name: 'Remove' }),
    );

    expect(chaos.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(chaos.querySelector('input[name="chaosExceptionStack"]')).toHaveValue('platform-edited');
  });

  it('opens application links JSON editing in a modal instead of editing inline', () => {
    vi.spyOn(ReactModal, 'show').mockResolvedValue(buildApplication().attributes.instanceLinks);
    const { container } = renderConfig();
    const links = section(container, 'links');

    fireEvent.click(within(links).getByRole('button', { name: /edit as json/i }));

    expect(ReactModal.show).toHaveBeenCalledWith(
      expect.any(Function),
      expect.objectContaining({ sections: expect.any(Array) }),
      expect.objectContaining({ dialogClassName: 'modal-lg modal-fullscreen' }),
    );
    expect(within(links).queryByText('Links JSON')).not.toBeInTheDocument();
  });

  it('renders application links in the same horizontal form layout as other config sections', () => {
    const { container } = renderConfig();
    const links = section(container, 'links');

    expect(links.querySelector('.application-links-config.form-horizontal')).toBeInTheDocument();
    expect(within(links).getByText('Section Heading')).toBeInTheDocument();
    expect(within(links).getByText('Label')).toBeInTheDocument();
    expect(within(links).getByText('Path')).toBeInTheDocument();
    expect(links.querySelector('input[placeholder="Label, e.g. Health"]')).toBeInTheDocument();
    expect(links.querySelector('input[placeholder="Path, e.g. /health"]')).toBeInTheDocument();
  });

  it('rejects non-array links JSON in the edit modal', () => {
    vi.spyOn(ReactModal, 'show').mockResolvedValue(buildApplication().attributes.instanceLinks);
    const { container } = renderConfig();
    fireEvent.click(within(section(container, 'links')).getByRole('button', { name: /edit as json/i }));
    const [ModalComponent, modalProps] = (ReactModal.show as Mock).mock.lastCall;
    const closeModal = vi.fn();
    const modal = render(<ModalComponent {...modalProps} closeModal={closeModal} />);

    fireEvent.change(formControl(modal.container, 'Links JSON'), { target: { value: '{"title":"Main"}' } });
    fireEvent.submit(modal.container.querySelector('form')!);

    expect(screen.getByText('Links JSON must be an array of link sections.')).toBeInTheDocument();
    expect(closeModal).not.toHaveBeenCalled();
  });
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => (resolve = promiseResolve));
  return { promise, resolve };
}

function buildApplication(overrides: any = {}) {
  return {
    ...overrides,
    name: 'fnord',
    attributes: {
      accounts: ['test'],
      cloudProviders: ['aws'],
      customBanners: [],
      defaultFilteredTags: [],
      description: 'test app',
      email: 'user@example.com',
      instancePort: 80,
      instanceLinks: [{ title: 'Main', links: [{ title: 'Health', path: '/health' }] }],
      ...overrides.attributes,
    },
    clusters: [],
    dataSources: [],
    refresh: vi.fn(),
    getDataSource: () => ({ ready: () => Promise.resolve({ hasManagedResources: true }) }),
    serverGroups: { data: [] },
  };
}
