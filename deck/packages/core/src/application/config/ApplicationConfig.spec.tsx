import { mount, shallow } from 'enzyme';
import React from 'react';

import {
  ApplicationAttributes,
  ApplicationAttributesForm,
  ApplicationConfig,
  ApplicationConfigComponent,
  ApplicationDataSourceEditor,
  CheckboxField,
} from './ApplicationConfig';
import { SETTINGS } from '../../config/settings';
import { NotificationsList } from '../../notification';
import { PermissionsConfigurer } from '../modal/PermissionsConfigurer';
import { ReactModal } from '../../presentation';
import { Markdown } from '../../presentation/Markdown';
import { PageNavigator, PageSection } from '../../presentation/navigation';
import { ApplicationWriter } from '../service/ApplicationWriter';
import { TaskReader } from '../../task';
import { AccountService } from '../../account';
import { ReactSelectInput } from '../../presentation';
import { HelpField } from '../../help';
import { AuthenticationService } from '../../authentication';
import { ClusterMatcher } from '../../cluster';
import { ClusterMatches } from '../../widgets';
import { ConfigSectionFooter } from './footer/ConfigSectionFooter';

const routerProps = { stateService: { go: () => undefined } as any };

describe('<ApplicationConfig />', () => {
  let originalFeatures: typeof SETTINGS.feature;
  let originalSlack: typeof SETTINGS.slack;

  beforeEach(() => {
    originalFeatures = SETTINGS.feature;
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
  });

  afterEach(() => {
    SETTINGS.feature = originalFeatures;
    SETTINGS.slack = originalSlack;
  });

  it('renders application config sections directly in React without the Angular adapter', () => {
    const application = buildApplication();

    const wrapper = shallow(<ApplicationConfigComponent {...routerProps} app={application as any} />);
    wrapper.setState({ hasManagedResources: true });

    expect(wrapper.find(['Angular', 'JS', 'Adapter'].join('')).exists()).toBe(false);
    expect(wrapper.find(PageSection).map((section) => section.prop('label'))).toEqual([
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
    ]);
    expect(wrapper.find(PageSection).map((section) => section.prop('pageKey'))).toEqual([
      'location',
      'managed-resources',
      'notifications',
      'features',
      'links',
      'chaos',
      'traffic-guards',
      'snapshot',
      'banner',
      'default-filters',
      'delete',
    ]);
    expect(
      wrapper
        .find(PageSection)
        .findWhere((section) => section.prop('pageKey') === 'managed-resources')
        .prop('visible'),
    ).toBe(true);
  });

  it('redirects missing applications using the injected state service', () => {
    const stateService = { go: jasmine.createSpy('go') };

    shallow(
      <ApplicationConfigComponent
        app={buildApplication({ notFound: true }) as any}
        stateService={stateService as any}
      />,
    );

    expect(stateService.go).toHaveBeenCalledWith('home.infrastructure', null, { location: 'replace' });
  });

  it('renders NotificationsList with application notification props', () => {
    const notifications = [{ level: 'application', type: 'email', when: ['pipeline.failed'] }];
    const application = buildApplication({ attributes: { notifications } });

    const wrapper = shallow(<ApplicationConfigComponent {...routerProps} app={application as any} />);
    const notificationList = wrapper.find(NotificationsList);

    expect(
      wrapper
        .find('p')
        .filterWhere((paragraph) => paragraph.text() === 'You can edit notification settings for this application')
        .exists(),
    ).toBe(true);
    expect(notificationList.exists()).toBe(true);
    expect(notificationList.prop('application')).toBe(application as any);
    expect(notificationList.prop('level')).toBe('application');
    expect(notificationList.prop('notifications')).toEqual(notifications as any);
    expect(notificationList.prop('updateNotifications')).toEqual(jasmine.any(Function));
  });

  it('renders configured application attributes with an edit button', () => {
    const application = buildApplication({
      attributes: { appGroup: 'payments', aliases: 'pay', email: 'user@example.com' },
    });

    const wrapper = shallow(<ApplicationConfigComponent {...routerProps} app={application as any} />);
    const attributes = wrapper.find(ApplicationAttributes).dive();

    expect(attributes.text()).toContain('Owner');
    expect(attributes.text()).toContain('user@example.com');
    expect(attributes.text()).toContain('App Group');
    expect(attributes.text()).toContain('payments');
    expect(
      attributes
        .find('button')
        .filterWhere((button) => button.text().includes('Edit Application Attributes'))
        .exists(),
    ).toBe(true);
    expect(attributes.text()).not.toContain('Angular-owned');
  });

  it('renders only application attributes before the application is configured', () => {
    const application = buildApplication({ attributes: { email: null } });

    const wrapper = shallow(<ApplicationConfigComponent {...routerProps} app={application as any} />);
    const attributes = wrapper.find(ApplicationAttributes).dive();

    expect(wrapper.find(PageSection).map((section) => section.prop('label'))).toEqual(['Application Attributes']);
    expect(React.Children.toArray(wrapper.find(PageNavigator).prop('children')).length).toBe(1);
    expect(attributes.text()).toContain('This application has not been configured.');
    expect(
      attributes
        .find('button')
        .filterWhere((button) => button.text().includes('Create Application'))
        .exists(),
    ).toBe(true);
    expect(attributes.text()).not.toContain('Angular edit modal');
  });

  it('opens application attributes in a modal instead of editing inline', () => {
    const application = buildApplication();
    spyOn(ReactModal, 'show').and.returnValue(Promise.resolve(application.attributes));

    const wrapper = shallow(<ApplicationConfigComponent {...routerProps} app={application as any} />);
    const attributes = wrapper.find(ApplicationAttributes).dive();

    attributes
      .find('button')
      .filterWhere((button) => button.text().includes('Edit Application Attributes'))
      .simulate('click');

    expect(ReactModal.show).toHaveBeenCalledWith(
      ApplicationAttributesForm,
      jasmine.objectContaining({ application, isConfigured: true }),
      jasmine.objectContaining({ dialogClassName: 'modal-lg' }),
    );
    expect(attributes.find(ApplicationAttributesForm).exists()).toBe(false);
  });

  it('renders configured sections after application attributes are saved', () => {
    const application = buildApplication({ attributes: { email: null } });

    const wrapper = shallow(<ApplicationConfigComponent {...routerProps} app={application as any} />);

    expect(wrapper.find(PageSection).map((section) => section.prop('label'))).toEqual(['Application Attributes']);

    wrapper.find(ApplicationAttributes).prop('onAttributesSaved')({ email: 'user@example.com' });
    wrapper.update();

    expect(wrapper.find(PageSection).map((section) => section.prop('label'))).toContain('Notifications');
    expect(wrapper.find(PageSection).map((section) => section.prop('label'))).toContain('Delete Application');
  });

  it('does not render accounts as an editable application attributes field', () => {
    const application = buildApplication();

    const form = shallow(
      <ApplicationAttributesForm
        application={application as any}
        isConfigured={true}
        onAttributesSaved={jasmine.createSpy('onAttributesSaved')}
      />,
    );

    expect(form.find('TextField[label="Account(s)"]').exists()).toBe(false);
    expect(form.find('.small.text-muted').text()).toBe('Accounts are managed by Front50 and cannot be changed here.');
  });

  it('renders the application name as read-only in the attributes modal', () => {
    const application = buildApplication();

    const form = shallow(
      <ApplicationAttributesForm
        application={application as any}
        isConfigured={true}
        onAttributesSaved={jasmine.createSpy('onAttributesSaved')}
      />,
    );

    expect(
      form
        .find('.form-control-static')
        .filterWhere((field) => field.text() === 'fnord')
        .exists(),
    ).toBe(true);
    expect(form.find('TextField[label="Name"]').exists()).toBe(false);
  });

  it('renders source repo type as a selector and only shows repo fields after a type is selected', () => {
    const originalGitSources = SETTINGS.gitSources;
    SETTINGS.gitSources = ['github', 'gitlab'];
    const application = buildApplication({ attributes: { repoType: '', repoProjectKey: '', repoSlug: '' } });

    const form = shallow(
      <ApplicationAttributesForm
        application={application as any}
        isConfigured={true}
        onAttributesSaved={jasmine.createSpy('onAttributesSaved')}
      />,
    );

    const repoTypeSelect = form.find('select[name="repoType"]');
    expect(repoTypeSelect.exists()).toBe(true);
    expect(repoTypeSelect.find('option').map((option) => option.prop('value'))).toEqual(['', 'github', 'gitlab']);
    expect(form.find('TextField[label="Repo Project"]').exists()).toBe(false);
    expect(form.find('TextField[label="Repo Name"]').exists()).toBe(false);

    repoTypeSelect.simulate('change', { target: { value: 'github' } });
    expect(form.find('TextField[label="Repo Project"]').exists()).toBe(true);
    expect(form.find('TextField[label="Repo Name"]').exists()).toBe(true);

    SETTINGS.gitSources = originalGitSources;
  });

  it('renders application attribute help fields from the old edit modal', () => {
    SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled: true };
    const application = buildApplication();

    const form = shallow(
      <ApplicationAttributesForm
        application={application as any}
        isConfigured={true}
        onAttributesSaved={jasmine.createSpy('onAttributesSaved')}
      />,
    );

    expect(form.find(CheckboxField).map((checkbox) => checkbox.prop('helpFieldId'))).toEqual(
      jasmine.arrayContaining([
        'application.platformHealthOnly',
        'application.showPlatformHealthOverride',
        'application.enableRestartRunningExecutions',
        'application.enableRerunActiveExecutions',
      ]),
    );
    expect(form.find('TextField[label="Instance Port"]').prop('helpFieldId')).toBe('application.instance.port');
    expect(form.find(HelpField).map((helpField) => helpField.prop('id'))).toContain('application.permissions');
  });

  it('renders cloud providers as a multi-select instead of checkboxes', async () => {
    const application = buildApplication();
    spyOn(AccountService, 'listProviders').and.returnValue(Promise.resolve(['aws', 'gce']) as any);

    const form = mount(
      <ApplicationAttributesForm
        application={application as any}
        isConfigured={true}
        onAttributesSaved={jasmine.createSpy('onAttributesSaved')}
      />,
    );

    await Promise.resolve();
    form.update();

    expect(
      form
        .find('label.checkbox-inline')
        .filterWhere((label) => label.text().includes('aws'))
        .exists(),
    ).toBe(false);
    expect(
      form
        .find(ReactSelectInput)
        .filterWhere((input) => input.prop('name') === 'cloudProviders')
        .prop('multi'),
    ).toBe(true);

    form.unmount();
  });

  it('renders application attribute modal group labels for health and pipeline options', () => {
    const application = buildApplication();

    const form = shallow(
      <ApplicationAttributesForm
        application={application as any}
        isConfigured={true}
        onAttributesSaved={jasmine.createSpy('onAttributesSaved')}
      />,
    );

    expect(form.find(CheckboxField).map((checkbox) => checkbox.prop('groupLabel'))).toContain('Instance Health');
    expect(form.find(CheckboxField).map((checkbox) => checkbox.prop('groupLabel'))).toContain('Pipeline Behavior');
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

    const features = shallow(<ApplicationDataSourceEditor application={application as any} />);

    expect(features.find('strong.application-feature-name').text()).toBe('Server Groups');
    expect(features.find('.application-feature-description').find(Markdown).prop('message')).toBe(
      'Shows server groups for this app.',
    );
  });

  it('edits permissions with the permissions configurer instead of raw JSON', () => {
    SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled: true };
    const application = buildApplication({
      attributes: { permissions: { READ: ['readers'], EXECUTE: [], WRITE: [] } },
    });

    const form = shallow(
      <ApplicationAttributesForm
        application={application as any}
        isConfigured={true}
        onAttributesSaved={jasmine.createSpy('onAttributesSaved')}
      />,
    );

    expect(form.find('TextAreaField[label="Permissions JSON"]').exists()).toBe(false);
    expect(form.find(PermissionsConfigurer).prop('permissions')).toEqual({ READ: ['readers'], EXECUTE: [], WRITE: [] });
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
        const application = buildApplication({ attributes: { permissions } });
        spyOn(ApplicationWriter, 'updateApplication').and.returnValue(new Promise(() => {}) as any);
        const form = shallow(
          <ApplicationAttributesForm
            application={application as any}
            isConfigured={true}
            onAttributesSaved={jasmine.createSpy('onAttributesSaved')}
          />,
        );

        form.simulate('submit', { preventDefault: jasmine.createSpy('preventDefault') });

        expect(form.find('.error-message').map((message) => message.text())).toEqual([
          'Permission groups cannot be empty.',
        ]);
        expect(ApplicationWriter.updateApplication).not.toHaveBeenCalled();
      });
    });
  });

  it('rejects read permissions without write permissions when fiat is enabled', () => {
    SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled: true };
    const application = buildApplication({
      attributes: { permissions: { READ: ['readers'], WRITE: [], EXECUTE: [] } },
    });
    spyOn(ApplicationWriter, 'updateApplication').and.returnValue(new Promise(() => {}) as any);
    const form = shallow(
      <ApplicationAttributesForm
        application={application as any}
        isConfigured={true}
        onAttributesSaved={jasmine.createSpy('onAttributesSaved')}
      />,
    );

    form.simulate('submit', { preventDefault: jasmine.createSpy('preventDefault') });

    expect(form.find('.error-message').map((message) => message.text())).toEqual([
      'Write permission is required when read permission is configured.',
    ]);
    expect(ApplicationWriter.updateApplication).not.toHaveBeenCalled();
  });

  it('allows hidden invalid legacy permissions when fiat is disabled', () => {
    const application = buildApplication({
      attributes: { permissions: { READ: ['readers', ''], WRITE: [], EXECUTE: [null] } },
    });
    spyOn(ApplicationWriter, 'updateApplication').and.returnValue(new Promise(() => {}) as any);
    const form = shallow(
      <ApplicationAttributesForm
        application={application as any}
        isConfigured={true}
        onAttributesSaved={jasmine.createSpy('onAttributesSaved')}
      />,
    );

    form.simulate('submit', { preventDefault: jasmine.createSpy('preventDefault') });

    expect(form.find('.error-message').exists()).toBe(false);
    expect(ApplicationWriter.updateApplication).toHaveBeenCalled();
  });

  it('allows saving permissions that warn about locking out the current user', () => {
    SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled: true };
    spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({ roles: ['current-user-group'] } as any);
    const application = buildApplication({
      attributes: {
        permissions: { READ: ['other-group'], WRITE: ['other-group'], EXECUTE: ['other-group'] },
      },
    });
    spyOn(ApplicationWriter, 'updateApplication').and.returnValue(new Promise(() => {}) as any);
    const form = shallow(
      <ApplicationAttributesForm
        application={application as any}
        isConfigured={true}
        onAttributesSaved={jasmine.createSpy('onAttributesSaved')}
      />,
    );

    form.simulate('submit', { preventDefault: jasmine.createSpy('preventDefault') });

    expect(form.find('.error-message').exists()).toBe(false);
    expect(ApplicationWriter.updateApplication).toHaveBeenCalled();
  });

  it('preserves batched application attribute updates when saving', async () => {
    const application = buildApplication({ attributes: { appGroup: '', email: 'old@example.com' } });
    spyOn(ApplicationWriter, 'updateApplication').and.returnValue(Promise.resolve({ id: '1' }) as any);
    const waitUntilTaskCompletes = spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(
      Promise.resolve({}) as any,
    );
    const form = shallow(
      <ApplicationAttributesForm
        application={application as any}
        isConfigured={true}
        onAttributesSaved={jasmine.createSpy('onAttributesSaved')}
      />,
    );

    form.find('TextField[label="Owner Email"]').prop('onChange')('new@example.com');
    form.find('TextField[label="App Group"]').prop('onChange')('payments');
    form.update();
    form.simulate('submit', { preventDefault: jasmine.createSpy('preventDefault') });
    await Promise.resolve();

    expect(ApplicationWriter.updateApplication).toHaveBeenCalledWith(
      jasmine.objectContaining({ appGroup: 'payments', email: 'new@example.com' }),
    );
    expect(waitUntilTaskCompletes).toHaveBeenCalled();
  });

  it('rejects fractional instance ports', () => {
    const application = buildApplication();
    const onAttributesSaved = jasmine.createSpy('onAttributesSaved');
    const form = shallow(
      <ApplicationAttributesForm
        application={application as any}
        isConfigured={true}
        onAttributesSaved={onAttributesSaved}
      />,
    );

    form.find('TextField[label="Instance Port"]').prop('onChange')('80.5');
    form.simulate('submit', { preventDefault: jasmine.createSpy('preventDefault') });

    const errorMessage = form.find('.error-message');
    expect(errorMessage.exists()).toBe(true);
    expect(errorMessage.text()).toBe('Instance port must be a whole number between 0 and 65535.');
    expect(onAttributesSaved).not.toHaveBeenCalled();
  });

  it('clears saving state after saving attributes without a modal close handler', async () => {
    const application = buildApplication();
    const onAttributesSaved = jasmine.createSpy('onAttributesSaved');
    spyOn(ApplicationWriter, 'updateApplication').and.returnValue(Promise.resolve({ id: '1' }) as any);
    spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(Promise.resolve({}) as any);
    const form = shallow(
      <ApplicationAttributesForm
        application={application as any}
        isConfigured={true}
        onAttributesSaved={onAttributesSaved}
      />,
    );

    form.simulate('submit', { preventDefault: jasmine.createSpy('preventDefault') });
    expect(form.find('button[type="submit"]').text()).toBe('Saving...');

    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    form.update();

    expect(onAttributesSaved).toHaveBeenCalled();
    expect(form.find('button[type="submit"]').text()).toBe('Save Changes');
  });

  it('renders Slack and PagerDuty application metadata when present', () => {
    const application = buildApplication({
      attributes: {
        pdApiKey: 'pager-duty-service',
        slackChannel: { id: 'C1234', name: 'deployments' },
      },
    });

    const wrapper = shallow(<ApplicationConfigComponent {...routerProps} app={application as any} />);
    const attributes = wrapper.find(ApplicationAttributes).dive();

    expect(attributes.text()).toContain('Pager Duty');
    expect(attributes.text()).toContain('pager-duty-service');
    expect(attributes.text()).toContain('Slack Channel');
    expect(attributes.find('a[href="https://slack.example.com/app_redirect?channel=C1234"]').text()).toBe(
      '#deployments',
    );
  });

  it('hides permissions when fiat is disabled', () => {
    const application = buildApplication({ attributes: { permissions: { READ: ['readers'] } } });

    const wrapper = shallow(<ApplicationConfigComponent {...routerProps} app={application as any} />);
    const attributes = wrapper.find(ApplicationAttributes).dive();

    expect(attributes.text()).not.toContain('Permissions');
    expect(attributes.text()).not.toContain('readers (read)');
  });

  it('renders permissions when fiat is enabled', () => {
    SETTINGS.feature = { ...SETTINGS.feature, fiatEnabled: true };
    const application = buildApplication({ attributes: { permissions: { READ: ['readers'] } } });

    const wrapper = shallow(<ApplicationConfigComponent {...routerProps} app={application as any} />);
    const attributes = wrapper.find(ApplicationAttributes).dive();

    expect(attributes.text()).toContain('Permissions');
    expect(attributes.text()).toContain('readers (read)');
  });

  it('renders functional React sections instead of migration blockers', () => {
    const application = buildApplication();

    const wrapper = shallow(<ApplicationConfigComponent {...routerProps} app={application as any} />);

    expect(wrapper.text()).not.toContain('still backed by');
    expect(wrapper.find('ApplicationLinksConfig').exists()).toBe(true);
    expect(wrapper.find('ChaosMonkeyConfigSection').exists()).toBe(true);
    expect(wrapper.find('TrafficGuardConfigSection').exists()).toBe(true);
    expect(wrapper.find('ApplicationSnapshotSection').exists()).toBe(true);
  });

  it('loads region-capable accounts and preserves unknown persisted Chaos Monkey exception values', async () => {
    const application = buildApplication({
      attributes: {
        chaosMonkey: {
          exceptions: [{ account: 'legacy', region: 'moon-1', stack: 'payments', detail: 'api' }],
        },
      },
    });
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve({
        aws: { name: 'aws', regions: [{ name: 'eu-west-1' }] },
        kubernetes: { name: 'kubernetes', namespaces: ['default'] },
      }) as any,
    );

    const chaos = mountChaosMonkeyConfig(application);
    await Promise.resolve();
    await Promise.resolve();
    chaos.update();

    expect(chaos.find('input[placeholder="account"]').exists()).toBe(false);
    expect(chaos.find('input[placeholder="region"]').exists()).toBe(false);
    expect(chaos.find('select[name="chaosExceptionAccount"] option').map((option) => option.prop('value'))).toEqual([
      '',
      'aws',
      'legacy',
    ]);
    expect(chaos.find('select[name="chaosExceptionRegion"] option').map((option) => option.prop('value'))).toEqual([
      '*',
      'moon-1',
    ]);

    chaos.unmount();
  });

  it('resets the Chaos Monkey exception region when its account changes and keeps stack and detail editable', async () => {
    const application = buildApplication({
      attributes: {
        chaosMonkey: {
          exceptions: [{ account: 'legacy', region: 'moon-1', stack: 'payments', detail: 'api' }],
        },
      },
    });
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve({ aws: { name: 'aws', regions: [{ name: 'eu-west-1' }] } }) as any,
    );
    const chaos = mountChaosMonkeyConfig(application);
    await Promise.resolve();
    await Promise.resolve();
    chaos.update();

    chaos.find('select[name="chaosExceptionAccount"]').simulate('change', { target: { value: 'aws' } });
    chaos.find('input[name="chaosExceptionStack"]').simulate('change', { target: { value: 'platform' } });
    chaos.find('input[name="chaosExceptionDetail"]').simulate('change', { target: { value: 'worker' } });
    chaos.update();

    expect(chaos.find('select[name="chaosExceptionRegion"]').prop('value')).toBe('*');
    expect(chaos.find('input[name="chaosExceptionStack"]').prop('value')).toBe('platform');
    expect(chaos.find('input[name="chaosExceptionDetail"]').prop('value')).toBe('worker');

    chaos.unmount();
  });

  it('waits for server groups before rendering sorted cluster matches for every Chaos Monkey exception', async () => {
    let resolveServerGroups: () => void;
    const serverGroupsReady = new Promise<void>((resolve) => {
      resolveServerGroups = resolve;
    });
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
      {
        account: 'prod',
        name: 'fnord-payments-alpha',
        serverGroups: [{ region: 'us-east-1' }],
      },
      { account: 'prod', name: 'fnord-other', serverGroups: [{ region: 'eu-west-1' }] },
    ];
    application.getDataSource = jasmine.createSpy('getDataSource').and.returnValue({ ready: () => serverGroupsReady });
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve({ prod: { name: 'prod', regions: [{ name: 'us-east-1' }] } }) as any,
    );
    spyOn(ClusterMatcher, 'getMatchingRule').and.callThrough();

    const chaos = mountChaosMonkeyConfig(application);
    await Promise.resolve();
    await Promise.resolve();

    expect(application.getDataSource).toHaveBeenCalledWith('serverGroups');
    expect(ClusterMatcher.getMatchingRule).not.toHaveBeenCalled();

    resolveServerGroups!();
    await Promise.resolve();
    await Promise.resolve();
    chaos.update();

    expect(ClusterMatcher.getMatchingRule).toHaveBeenCalledWith('prod', 'us-west-2', 'fnord-payments-zeta', [
      jasmine.objectContaining({ account: 'prod', location: '*', stack: 'payments', detail: '*' }),
    ]);
    expect(chaos.find(ClusterMatches)).toHaveSize(2);
    expect(chaos.find(ClusterMatches).at(0).prop('matches')).toEqual([
      { account: 'prod', name: 'fnord-payments-alpha', regions: ['us-east-1'] },
      { account: 'prod', name: 'fnord-payments-zeta', regions: ['us-east-1', 'us-west-2'] },
    ]);
    expect(chaos.find(ClusterMatches).at(1).text()).toBe('(no matches)');

    chaos.unmount();
  });

  (['credentials', 'server groups'] as const).forEach((failureSource) => {
    it(`shows unavailable matching when ${failureSource} fail to load`, async () => {
      const application = buildApplication({
        attributes: {
          chaosMonkey: {
            exceptions: [{ account: 'prod', region: '*', stack: 'payments', detail: '*' }],
          },
        },
      });
      const failure = new Error(`${failureSource} unavailable`);
      spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
        failureSource === 'credentials'
          ? (Promise.reject(failure) as any)
          : (Promise.resolve({ prod: { name: 'prod', regions: [{ name: 'eu-west-1' }] } }) as any),
      );
      application.getDataSource = jasmine.createSpy('getDataSource').and.returnValue({
        ready: () => (failureSource === 'server groups' ? Promise.reject(failure) : Promise.resolve()),
      });

      const chaos = mountChaosMonkeyConfig(application);
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
      chaos.update();

      expect(chaos.find('.chaos-matches-unavailable').text()).toBe('(matches unavailable)');
      expect(chaos.find(ClusterMatches).exists()).toBe(false);

      chaos.unmount();
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
    application.getDataSource = jasmine.createSpy('getDataSource').and.returnValue({ ready: () => Promise.resolve() });
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve({ prod: { name: 'prod', regions: [{ name: 'eu-west-1' }, { name: 'us-east-1' }] } }) as any,
    );
    spyOn(ClusterMatcher, 'getMatchingRule').and.callThrough();
    spyOn(ApplicationWriter, 'updateApplication').and.returnValue(Promise.resolve({ id: '1' }) as any);
    spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(Promise.resolve({}) as any);

    const chaos = mountChaosMonkeyConfig(application);
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    chaos.update();

    expect(chaos.find('select[name="chaosExceptionRegion"]').map((select) => select.prop('value'))).toEqual(['*', '*']);
    expect((ClusterMatcher.getMatchingRule as jasmine.Spy).calls.allArgs().map((args) => args[3][0])).toEqual(
      jasmine.arrayContaining([
        jasmine.objectContaining({ location: '*', stack: 'payments' }),
        jasmine.objectContaining({ location: '*', stack: 'platform' }),
      ]),
    );

    chaos.find(ConfigSectionFooter).prop('onSaveClicked')();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();

    expect(ApplicationWriter.updateApplication).toHaveBeenCalledWith(
      jasmine.objectContaining({
        chaosMonkey: jasmine.objectContaining({
          exceptions: [
            { account: 'prod', region: '*', stack: 'payments', detail: '*' },
            { account: 'prod', region: '*', stack: 'platform', detail: '*' },
          ],
        }),
      }),
    );

    chaos.unmount();
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
    application.getDataSource = jasmine.createSpy('getDataSource').and.returnValue({ ready: () => Promise.resolve() });
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve({ prod: { name: 'prod', regions: [{ name: 'eu-west-1' }] } }) as any,
    );
    const chaos = mountChaosMonkeyConfig(application);
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
    chaos.update();
    const secondMatches = chaos.find(ClusterMatches).at(1).instance();

    chaos.find('tbody button').at(0).simulate('click');
    chaos.update();

    expect(chaos.find(ClusterMatches)).toHaveSize(1);
    expect(chaos.find(ClusterMatches).at(0).instance()).toBe(secondMatches);

    chaos.unmount();
  });

  it('opens application links JSON editing in a modal instead of editing inline', () => {
    const application = buildApplication();
    spyOn(ReactModal, 'show').and.returnValue(Promise.resolve(application.attributes.instanceLinks));

    const wrapper = shallow(<ApplicationConfigComponent {...routerProps} app={application as any} />);
    const links = wrapper.find('ApplicationLinksConfig').dive();

    links
      .find('button')
      .filterWhere((button) => button.text().includes('Edit as JSON'))
      .simulate('click');

    expect(ReactModal.show).toHaveBeenCalledWith(
      jasmine.any(Function),
      jasmine.objectContaining({ sections: jasmine.any(Array) }),
      jasmine.objectContaining({ dialogClassName: 'modal-lg modal-fullscreen' }),
    );
    expect(links.find('TextAreaField[label="Links JSON"]').exists()).toBe(false);
  });

  it('renders application links in the same horizontal form layout as other config sections', () => {
    const application = buildApplication();

    const wrapper = shallow(<ApplicationConfigComponent {...routerProps} app={application as any} />);
    const links = wrapper.find('ApplicationLinksConfig').dive();

    expect(links.find('.application-links-config.form-horizontal').exists()).toBe(true);
    expect(links.find('TextField[label="Section Heading"]').exists()).toBe(true);
    expect(links.find('TextField[label="Label"]').exists()).toBe(true);
    expect(links.find('TextField[label="Path"]').exists()).toBe(true);
    expect(links.find('input[placeholder="Label, e.g. Health"]').exists()).toBe(false);
    expect(links.find('input[placeholder="Path, e.g. /health"]').exists()).toBe(false);
  });

  it('rejects non-array links JSON in the edit modal', () => {
    const application = buildApplication();
    spyOn(ReactModal, 'show').and.returnValue(Promise.resolve(application.attributes.instanceLinks));

    const wrapper = shallow(<ApplicationConfigComponent {...routerProps} app={application as any} />);
    const links = wrapper.find('ApplicationLinksConfig').dive();
    links
      .find('button')
      .filterWhere((button) => button.text().includes('Edit as JSON'))
      .simulate('click');
    const [ModalComponent, modalProps] = (ReactModal.show as jasmine.Spy).calls.mostRecent().args;
    const closeModal = jasmine.createSpy('closeModal');
    const modal = shallow(<ModalComponent {...modalProps} closeModal={closeModal} />);

    modal.find('TextAreaField[label="Links JSON"]').prop('onChange')('{"title":"Main"}');
    modal.simulate('submit', { preventDefault: jasmine.createSpy('preventDefault') });

    const errorMessage = modal.find('.error-message');
    expect(errorMessage.exists()).toBe(true);
    expect(errorMessage.text()).toBe('Links JSON must be an array of link sections.');
    expect(closeModal).not.toHaveBeenCalled();
  });
});

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
    refresh: jasmine.createSpy('refresh'),
    getDataSource: () => ({
      ready: () => Promise.resolve({ hasManagedResources: true }),
    }),
    serverGroups: { data: [] },
  };
}

function mountChaosMonkeyConfig(application: any) {
  const config = shallow(<ApplicationConfigComponent {...routerProps} app={application} />, {
    disableLifecycleMethods: true,
  }).find('ChaosMonkeyConfigSection');
  const ChaosMonkeyConfigSection = config.type() as React.ComponentType<{ application: any }>;
  return mount(<ChaosMonkeyConfigSection application={application} />);
}
