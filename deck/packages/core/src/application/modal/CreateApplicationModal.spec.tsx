import type { ShallowWrapper } from 'enzyme';
import { shallow } from 'enzyme';
import React from 'react';

import type { IApplicationAttributes } from '../service/ApplicationWriter';
import { ApplicationReader } from '../service/ApplicationReader';
import { ApplicationWriter } from '../service/ApplicationWriter';
import { CreateApplicationModal, validateCreateApplication } from './CreateApplicationModal';
import { ApplicationProviderFields } from './ApplicationProviderFields';
import { PermissionsConfigurer } from './PermissionsConfigurer';
import { PlatformHealthOverride } from './PlatformHealthOverride';
import { AccountService } from '../../account/AccountService';
import { SETTINGS } from '../../config/settings';
import { PagerDutySelectField } from '../../pagerDuty/PagerDutySelectField';
import { ReactModal, ReactSelectInput } from '../../presentation';
import SlackChannelSelector from '../../slack/SlackChannelSelector';
import { TaskReader } from '../../task/task.read.service';
import { ApplicationNameValidator } from './validation/ApplicationNameValidator';

function deferred<T>() {
  let resolve: (value: T) => void;
  let reject: (reason?: any) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, reject: reject!, resolve: resolve! };
}

describe('CreateApplicationModal', () => {
  let originalFeatures: typeof SETTINGS.feature;
  let originalNewApplicationDefaults: typeof SETTINGS.newApplicationDefaults;
  let originalPagerDuty: typeof SETTINGS.pagerDuty;
  let wrapper: ShallowWrapper | undefined;

  beforeEach(() => {
    wrapper = undefined;
    originalFeatures = SETTINGS.feature;
    originalNewApplicationDefaults = SETTINGS.newApplicationDefaults;
    originalPagerDuty = SETTINGS.pagerDuty;
    SETTINGS.feature = { ...SETTINGS.feature, chaosMonkey: false, fiatEnabled: false, pagerDuty: false, slack: false };
    SETTINGS.newApplicationDefaults = { chaosMonkey: true };
    spyOn(ApplicationReader, 'listApplications').and.returnValue(Promise.resolve([]));
    spyOn(AccountService, 'listProviders').and.returnValue(Promise.resolve(['aws']));
    spyOn(ApplicationNameValidator, 'validate').and.returnValue(Promise.resolve({ errors: [], warnings: [] }));
  });

  afterEach(() => {
    wrapper?.unmount();
    SETTINGS.feature = originalFeatures;
    SETTINGS.newApplicationDefaults = originalNewApplicationDefaults;
    SETTINGS.pagerDuty = originalPagerDuty;
  });

  it('shows a large direct React modal with the deep-link name', () => {
    spyOn(ReactModal, 'show').and.returnValue(Promise.resolve({}) as any);

    CreateApplicationModal.show('DeepLinkApp');

    expect(ReactModal.show).toHaveBeenCalledWith(
      CreateApplicationModal,
      { name: 'DeepLinkApp' },
      { dialogClassName: 'modal-lg' },
    );
  });

  it('validates required fields, lowercase duplicates, repository slugs, ports, permissions, and health warning ack', () => {
    const invalid: IApplicationAttributes = {
      name: 'MYAPP',
      email: ' invalid ',
      repoSlug: 'https://example.test/repo',
      instancePort: 65536,
      cloudProviders: [],
      permissions: { READ: ['team'], EXECUTE: [], WRITE: [] },
      platformHealthOnlyShowOverride: true,
    };

    const result = validateCreateApplication(invalid, ['myapp'], false);

    expect(result.errors).toContain('Application name must be unique.');
    expect(result.errors).toContain('Please enter a valid email address.');
    expect(result.errors).toContain('Enter your source repository name (not the URL).');
    expect(result.errors).toContain('Instance port must be an integer between 0 and 65535.');
    expect(result.errors).toContain('Permissions must include a write group when read groups are configured.');
    expect(result.errors).toContain('Acknowledge the platform health override warning.');

    expect(validateCreateApplication({ name: '', email: '' }, [], true).errors).toEqual([
      'Application name is required.',
      'Owner email is required.',
    ]);
  });

  it('requires a selected PagerDuty service when PagerDuty is required', () => {
    const application = { name: 'myapp', email: 'owner@example.com' };

    expect(validateCreateApplication(application, [], true, true).errors).toContain('PagerDuty service is required.');
    expect(
      validateCreateApplication({ ...application, pdApiKey: 'integration-key' }, [], true, true).errors,
    ).not.toContain('PagerDuty service is required.');
  });

  it('guards submission when required PagerDuty is missing despite form noValidate', async () => {
    SETTINGS.feature = { ...SETTINGS.feature, pagerDuty: true };
    SETTINGS.pagerDuty = { ...SETTINGS.pagerDuty, required: true };
    spyOn(ApplicationWriter, 'createApplication').and.returnValue(Promise.resolve({ id: 'pager-duty' }) as any);
    spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(Promise.resolve({ id: 'pager-duty' }) as any);
    wrapper = shallow(<CreateApplicationModal name="myapp" />);
    wrapper.setState({
      application: { ...(wrapper.instance() as CreateApplicationModal).state.application, email: 'owner@example.com' },
      initializing: false,
    });
    const instance = wrapper.instance() as CreateApplicationModal;

    expect(wrapper.find('[data-purpose="create-application"]').prop('disabled')).toBe(true);
    await (instance as any).submit();
    expect(ApplicationWriter.createApplication).not.toHaveBeenCalled();

    instance.setState({ application: { ...instance.state.application, pdApiKey: 'integration-key' } });
    wrapper.update();
    expect(wrapper.find('[data-purpose="create-application"]').prop('disabled')).toBe(false);
  });

  it('does not require a hidden PagerDuty field when the feature is disabled', async () => {
    SETTINGS.feature = { ...SETTINGS.feature, pagerDuty: false };
    SETTINGS.pagerDuty = { ...SETTINGS.pagerDuty, required: true };
    spyOn(ApplicationWriter, 'createApplication').and.returnValue(Promise.resolve({ id: 'no-pager-duty' }) as any);
    spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(Promise.resolve({ id: 'no-pager-duty' }) as any);
    wrapper = shallow(<CreateApplicationModal name="myapp" />);
    wrapper.setState({
      application: { ...(wrapper.instance() as CreateApplicationModal).state.application, email: 'owner@example.com' },
      initializing: false,
    });
    const instance = wrapper.instance() as CreateApplicationModal;

    expect(wrapper.find(PagerDutySelectField).exists()).toBe(false);
    expect(wrapper.find('[data-purpose="create-application"]').prop('disabled')).toBe(false);

    await (instance as any).submit();
    expect(ApplicationWriter.createApplication).toHaveBeenCalledTimes(1);
  });

  it('associates native field labels with their inputs', () => {
    wrapper = shallow(<CreateApplicationModal name="myapp" />);
    wrapper.setState({ initializing: false });

    ['name', 'email', 'repoType', 'description', 'instancePort'].forEach((field) => {
      expect(wrapper.find(`label[htmlFor="${field}"]`).exists()).toBe(true);
      expect(wrapper.find(`#${field}`).exists()).toBe(true);
    });
  });

  it('renders direct provider, health, PagerDuty, Slack, Chaos Monkey, and permissions controls', () => {
    SETTINGS.feature = { ...SETTINGS.feature, chaosMonkey: true, fiatEnabled: true, pagerDuty: true, slack: true };
    wrapper = shallow(<CreateApplicationModal name="app" />);
    wrapper.setState({ initializing: false });

    expect(wrapper.find(ReactSelectInput).exists()).toBe(true);
    expect(wrapper.find(ApplicationProviderFields).exists()).toBe(true);
    expect(wrapper.find(PlatformHealthOverride).exists()).toBe(true);
    expect(wrapper.find(PagerDutySelectField).exists()).toBe(true);
    expect(wrapper.find(SlackChannelSelector).exists()).toBe(true);
    expect(wrapper.find(PermissionsConfigurer).exists()).toBe(true);
    expect(wrapper.find('[data-purpose="chaos-monkey-enabled"]').exists()).toBe(true);
    expect((wrapper.state() as any).application.chaosMonkey).toEqual({
      enabled: true,
      exceptions: [],
      grouping: 'cluster',
      meanTimeBetweenKillsInWorkDays: 2,
      minTimeBetweenKillsInWorkDays: 1,
      regionsAreIndependent: true,
    });
  });

  it('disables creation until synchronous validation passes', () => {
    wrapper = shallow(<CreateApplicationModal name="myapp" />);
    wrapper.setState({ initializing: false });

    expect(wrapper.find('[data-purpose="create-application"]').prop('disabled')).toBe(true);

    const instance = wrapper.instance() as CreateApplicationModal;
    instance.setState({ application: { ...instance.state.application, email: 'owner@example.com' } });
    wrapper.update();

    expect(wrapper.find('[data-purpose="create-application"]').prop('disabled')).toBe(false);
  });

  it('ignores stale provider validation completions', async () => {
    let resolveFirst: (result: any) => void;
    let resolveSecond: (result: any) => void;
    (ApplicationNameValidator.validate as jasmine.Spy).and.returnValues(
      new Promise((resolve) => (resolveFirst = resolve)),
      new Promise((resolve) => (resolveSecond = resolve)),
    );
    wrapper = shallow(<CreateApplicationModal name="first" />);
    await Promise.resolve();
    wrapper.update();
    const instance = wrapper.instance() as CreateApplicationModal;

    (instance as any).updateApplication('name', 'second');
    resolveSecond({ errors: [], warnings: [{ cloudProvider: 'aws', message: 'second warning' }] });
    await Promise.resolve();
    resolveFirst({ errors: [{ cloudProvider: 'aws', message: 'stale error' }], warnings: [] });
    await Promise.resolve();

    expect(instance.state.providerErrors).toEqual([]);
    expect(instance.state.providerWarnings.map((warning) => warning.message)).toEqual(['second warning']);
  });

  it('submits a cloned lowercase payload with the sole provider and closes only after task success', async () => {
    const task = { id: '1' } as any;
    let finishTask: (task: any) => void;
    const closeModal = jasmine.createSpy('closeModal');
    spyOn(ApplicationWriter, 'createApplication').and.returnValue(Promise.resolve(task));
    spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(
      new Promise((resolve) => (finishTask = resolve)) as any,
    );
    wrapper = shallow(<CreateApplicationModal name="MyApp" closeModal={closeModal} />);
    await Promise.resolve();
    wrapper.update();
    const instance = wrapper.instance() as CreateApplicationModal;
    instance.setState({
      application: {
        ...instance.state.application,
        email: 'owner@example.com',
        description: 'preserved',
        customField: { nested: true },
      },
      availableProviders: ['aws'],
    });

    const submission = (instance as any).submit();
    await Promise.resolve();
    await Promise.resolve();
    expect(closeModal).not.toHaveBeenCalled();
    const payload = (ApplicationWriter.createApplication as jasmine.Spy).calls.mostRecent().args[0];
    expect(payload.name).toBe('myapp');
    expect(payload.cloudProviders).toEqual(['aws']);
    expect(payload.customField).toEqual({ nested: true });
    expect(payload).not.toBe(instance.state.application);

    finishTask(task);
    await submission;
    expect(closeModal).toHaveBeenCalledWith(payload);
  });

  it('enters submitting synchronously and ignores a second submit during provider validation', async () => {
    const providerValidation = deferred<any>();
    spyOn(ApplicationWriter, 'createApplication').and.returnValue(Promise.resolve({ id: 'single-submit' }) as any);
    spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(Promise.resolve({ id: 'single-submit' }) as any);
    wrapper = shallow(<CreateApplicationModal name="myapp" />);
    await Promise.resolve();
    await Promise.resolve();
    const instance = wrapper.instance() as CreateApplicationModal;
    instance.setState({ application: { ...instance.state.application, email: 'owner@example.com' } });
    (ApplicationNameValidator.validate as jasmine.Spy).calls.reset();
    (ApplicationNameValidator.validate as jasmine.Spy).and.returnValue(providerValidation.promise);

    const firstSubmission = (instance as any).submit();
    const secondSubmission = (instance as any).submit();

    expect(instance.state.submitting).toBe(true);
    expect(ApplicationNameValidator.validate).toHaveBeenCalledTimes(1);

    providerValidation.resolve({ errors: [], warnings: [] });
    await firstSubmission;
    await secondSubmission;
    expect(ApplicationWriter.createApplication).toHaveBeenCalledTimes(1);
  });

  it('submits the application and providers snapshotted before async validation', async () => {
    const providerValidation = deferred<any>();
    spyOn(ApplicationWriter, 'createApplication').and.returnValue(Promise.resolve({ id: 'snapshot' }) as any);
    spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(Promise.resolve({ id: 'snapshot' }) as any);
    wrapper = shallow(<CreateApplicationModal name="original" />);
    await Promise.resolve();
    await Promise.resolve();
    const instance = wrapper.instance() as CreateApplicationModal;
    instance.setState({
      application: {
        ...instance.state.application,
        cloudProviders: ['aws'],
        description: 'validated draft',
        email: 'owner@example.com',
      },
      availableProviders: ['aws', 'gce'],
    });
    (ApplicationNameValidator.validate as jasmine.Spy).and.returnValue(providerValidation.promise);

    const submission = (instance as any).submit();
    instance.setState({
      application: {
        ...instance.state.application,
        cloudProviders: ['gce'],
        description: 'newer unvalidated draft',
        name: 'newer',
      },
    });
    providerValidation.resolve({ errors: [], warnings: [] });
    await submission;

    const payload = (ApplicationWriter.createApplication as jasmine.Spy).calls.mostRecent().args[0];
    expect(payload.name).toBe('original');
    expect(payload.cloudProviders).toEqual(['aws']);
    expect(payload.description).toBe('validated draft');
  });

  it('restores submission after provider validation fails so the user can retry', async () => {
    const failedValidation = deferred<any>();
    spyOn(ApplicationWriter, 'createApplication').and.returnValue(Promise.resolve({ id: 'retry' }) as any);
    spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(Promise.resolve({ id: 'retry' }) as any);
    wrapper = shallow(<CreateApplicationModal name="myapp" />);
    await Promise.resolve();
    await Promise.resolve();
    const instance = wrapper.instance() as CreateApplicationModal;
    instance.setState({ application: { ...instance.state.application, email: 'owner@example.com' } });
    (ApplicationNameValidator.validate as jasmine.Spy).and.returnValue(failedValidation.promise);

    const failedSubmission = (instance as any).submit();
    expect(instance.state.submitting).toBe(true);
    failedValidation.resolve({ errors: [{ cloudProvider: 'aws', message: 'invalid name' }], warnings: [] });
    await failedSubmission;
    expect(instance.state.submitting).toBe(false);
    expect(ApplicationWriter.createApplication).not.toHaveBeenCalled();

    (ApplicationNameValidator.validate as jasmine.Spy).and.returnValue(Promise.resolve({ errors: [], warnings: [] }));
    await (instance as any).submit();
    expect(ApplicationWriter.createApplication).toHaveBeenCalledTimes(1);
  });

  it('restores submission after provider validation rejects so the user can retry', async () => {
    const rejectedValidation = deferred<any>();
    spyOn(ApplicationWriter, 'createApplication').and.returnValue(Promise.resolve({ id: 'retry-rejection' }) as any);
    spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(Promise.resolve({ id: 'retry-rejection' }) as any);
    wrapper = shallow(<CreateApplicationModal name="myapp" />);
    await Promise.resolve();
    await Promise.resolve();
    const instance = wrapper.instance() as CreateApplicationModal;
    instance.setState({ application: { ...instance.state.application, email: 'owner@example.com' } });
    (ApplicationNameValidator.validate as jasmine.Spy).and.returnValue(rejectedValidation.promise);

    const failedSubmission = (instance as any).submit();
    rejectedValidation.reject(new Error('provider validation unavailable'));
    await failedSubmission.catch(() => undefined);
    wrapper.update();

    expect(ApplicationWriter.createApplication).not.toHaveBeenCalled();
    expect((instance as any).submissionInProgress).toBe(false);
    expect(instance.state.submitting).toBe(false);
    expect(instance.state.errorMessages).toEqual(['Could not validate application. Please try again.']);
    expect(wrapper.find('[data-purpose="cancel-create-application"]').prop('disabled')).toBe(false);
    expect(wrapper.find('[data-purpose="create-application"]').prop('disabled')).toBe(false);

    (ApplicationNameValidator.validate as jasmine.Spy).and.returnValue(Promise.resolve({ errors: [], warnings: [] }));
    await (instance as any).submit();
    expect(ApplicationWriter.createApplication).toHaveBeenCalledTimes(1);
  });

  it('shows retryable writer and task errors without closing', async () => {
    const closeModal = jasmine.createSpy('closeModal');
    spyOn(ApplicationWriter, 'createApplication').and.returnValues(
      Promise.reject(new Error('writer failed')),
      Promise.resolve({ id: '2' }) as any,
    );
    spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(
      Promise.reject({ failureMessage: 'task failed' }) as any,
    );
    wrapper = shallow(<CreateApplicationModal name="myapp" closeModal={closeModal} />);
    await Promise.resolve();
    wrapper.update();
    const instance = wrapper.instance() as CreateApplicationModal;
    instance.setState({ application: { ...instance.state.application, email: 'owner@example.com' } });
    wrapper.update();

    await (instance as any).submit();
    expect(instance.state.errorMessages).toEqual(['Could not create application']);
    await (instance as any).submit();
    expect(instance.state.errorMessages).toEqual(['Could not create application: task failed']);
    expect(instance.state.submitting).toBe(false);
    expect(closeModal).not.toHaveBeenCalled();
  });

  it('dismisses on cancel and ignores late async completion after unmount', async () => {
    let finishTask: (task: any) => void;
    const closeModal = jasmine.createSpy('closeModal');
    const dismissModal = jasmine.createSpy('dismissModal');
    spyOn(ApplicationWriter, 'createApplication').and.returnValue(Promise.resolve({ id: '3' }) as any);
    spyOn(TaskReader, 'waitUntilTaskCompletes').and.returnValue(
      new Promise((resolve) => (finishTask = resolve)) as any,
    );
    wrapper = shallow(<CreateApplicationModal name="myapp" closeModal={closeModal} dismissModal={dismissModal} />);
    wrapper.setState({ initializing: false });
    const instance = wrapper.instance() as CreateApplicationModal;
    instance.setState({ application: { ...instance.state.application, email: 'owner@example.com' } });

    (wrapper.find('[data-purpose="cancel-create-application"]').prop('onClick') as () => void)();
    expect(dismissModal).toHaveBeenCalledWith('cancel');
    const submission = (instance as any).submit();
    await Promise.resolve();
    wrapper.unmount();
    wrapper = undefined;
    finishTask({ id: '3' });
    await submission;

    expect(closeModal).not.toHaveBeenCalled();
  });
});
