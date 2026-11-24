import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { of } from 'rxjs';
import type { Mock } from 'vitest';

import type { IApplicationAttributes } from '../service/ApplicationWriter';
import { ApplicationReader } from '../service/ApplicationReader';
import { ApplicationWriter } from '../service/ApplicationWriter';
import { CreateApplicationModal, validateCreateApplication } from './CreateApplicationModal';
import { ApplicationNameValidator } from './validation/ApplicationNameValidator';
import { AccountService } from '../../account/AccountService';
import { SETTINGS } from '../../config/settings';
import { PagerDutyReader } from '../../pagerDuty/pagerDuty.read.service';
import { ReactModal } from '../../presentation';
import { SlackReader } from '../../slack';
import { TaskReader } from '../../task/task.read.service';

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

  beforeEach(() => {
    originalFeatures = SETTINGS.feature;
    originalNewApplicationDefaults = SETTINGS.newApplicationDefaults;
    originalPagerDuty = SETTINGS.pagerDuty;
    SETTINGS.feature = { ...SETTINGS.feature, chaosMonkey: false, fiatEnabled: false, pagerDuty: false, slack: false };
    SETTINGS.newApplicationDefaults = { chaosMonkey: true };
    vi.spyOn(ApplicationReader, 'listApplications').mockReturnValue(Promise.resolve([]));
    vi.spyOn(AccountService, 'listProviders').mockReturnValue(Promise.resolve(['aws']));
    vi.spyOn(ApplicationNameValidator, 'validate').mockReturnValue(Promise.resolve({ errors: [], warnings: [] }));
    vi.spyOn(PagerDutyReader, 'listServices').mockReturnValue(
      of([{ name: 'Payments', integration_key: 'integration-key' } as any]),
    );
    vi.spyOn(SlackReader, 'getChannels').mockResolvedValue([]);
  });

  afterEach(() => {
    SETTINGS.feature = originalFeatures;
    SETTINGS.newApplicationDefaults = originalNewApplicationDefaults;
    SETTINGS.pagerDuty = originalPagerDuty;
  });

  const renderModal = async (props: React.ComponentProps<typeof CreateApplicationModal> = {}) => {
    const rendered = render(<CreateApplicationModal {...props} />);
    await screen.findByLabelText(/owner email/i);
    return rendered;
  };

  const submit = () => fireEvent.click(screen.getByRole('button', { name: /^create$/i }));

  it('shows a large direct React modal with the deep-link name', () => {
    vi.spyOn(ReactModal, 'show').mockReturnValue(Promise.resolve({}) as any);

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
    vi.spyOn(ApplicationWriter, 'createApplication').mockResolvedValue({ id: 'pager-duty' } as any);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue({ id: 'pager-duty' } as any);
    const { container } = await renderModal({ name: 'myapp' });
    fireEvent.change(screen.getByLabelText(/owner email/i), { target: { value: 'owner@example.com' } });

    expect(screen.getByRole('button', { name: /^create$/i })).toBeDisabled();
    fireEvent.submit(container.querySelector('form')!);
    expect(ApplicationWriter.createApplication).not.toHaveBeenCalled();

    const pagerDuty = screen.getByRole('combobox', { name: 'PagerDuty service' });
    fireEvent.focus(pagerDuty);
    fireEvent.keyDown(pagerDuty, { key: 'ArrowDown', keyCode: 40 });
    fireEvent.keyDown(pagerDuty, { key: 'Enter', keyCode: 13 });
    await waitFor(() => expect(screen.getByRole('button', { name: /^create$/i })).toBeEnabled());
  });

  it('does not require a hidden PagerDuty field when the feature is disabled', async () => {
    SETTINGS.pagerDuty = { ...SETTINGS.pagerDuty, required: true };
    vi.spyOn(ApplicationWriter, 'createApplication').mockResolvedValue({ id: 'no-pager-duty' } as any);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue({ id: 'no-pager-duty' } as any);
    await renderModal({ name: 'myapp' });
    fireEvent.change(screen.getByLabelText(/owner email/i), { target: { value: 'owner@example.com' } });

    expect(screen.queryByText(/pagerduty \*/i)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^create$/i })).toBeEnabled();
    submit();
    await waitFor(() => expect(ApplicationWriter.createApplication).toHaveBeenCalledTimes(1));
  });

  it('associates native field labels with their inputs', async () => {
    await renderModal({ name: 'myapp' });

    ['Name', 'Owner Email', 'Repo Type', 'Description', 'Instance Port'].forEach((label) => {
      expect(screen.getByLabelText(new RegExp(label, 'i'))).toBeInTheDocument();
    });
  });

  it('renders direct provider, health, PagerDuty, Slack, Chaos Monkey, and permissions controls', async () => {
    SETTINGS.feature = { ...SETTINGS.feature, chaosMonkey: true, fiatEnabled: true, pagerDuty: true, slack: true };
    const { container } = await renderModal({ name: 'app' });

    expect(screen.getByText('Cloud Providers')).toBeInTheDocument();
    expect(screen.getByText(/instance health/i)).toBeInTheDocument();
    expect(screen.getAllByText(/pagerduty/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/slack/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/permissions/i)).toBeInTheDocument();
    expect(container.querySelector('[data-purpose="chaos-monkey-enabled"]')).toBeChecked();
  });

  it('disables creation until synchronous validation passes', async () => {
    await renderModal({ name: 'myapp' });

    expect(screen.getByRole('button', { name: /^create$/i })).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/owner email/i), { target: { value: 'owner@example.com' } });
    expect(screen.getByRole('button', { name: /^create$/i })).toBeEnabled();
  });

  it('ignores stale provider validation completions', async () => {
    const first = deferred<any>();
    const second = deferred<any>();
    (ApplicationNameValidator.validate as Mock).mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    await renderModal({ name: 'first' });

    fireEvent.change(screen.getByLabelText(/name/i), { target: { value: 'second' } });
    second.resolve({ errors: [], warnings: [{ cloudProvider: 'aws', message: 'second warning' }] });
    await screen.findByText(/second warning/i);
    first.resolve({ errors: [{ cloudProvider: 'aws', message: 'stale error' }], warnings: [] });
    await Promise.resolve();

    expect(screen.getByText(/second warning/i)).toBeInTheDocument();
    expect(screen.queryByText(/stale error/i)).not.toBeInTheDocument();
  });

  it('submits a cloned lowercase payload with the sole provider and closes only after task success', async () => {
    const task = { id: '1' } as any;
    const finishTask = deferred<any>();
    const closeModal = vi.fn();
    vi.spyOn(ApplicationWriter, 'createApplication').mockResolvedValue(task);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockReturnValue(finishTask.promise as any);
    await renderModal({ name: 'MyApp', closeModal });
    fireEvent.change(screen.getByLabelText(/owner email/i), { target: { value: 'owner@example.com' } });
    fireEvent.change(screen.getByLabelText(/description/i), { target: { value: 'preserved' } });

    submit();
    await waitFor(() => expect(ApplicationWriter.createApplication).toHaveBeenCalled());
    expect(closeModal).not.toHaveBeenCalled();
    const payload = (ApplicationWriter.createApplication as Mock).mock.lastCall[0];
    expect(payload).toEqual(
      expect.objectContaining({ name: 'myapp', cloudProviders: ['aws'], description: 'preserved' }),
    );

    finishTask.resolve(task);
    await waitFor(() => expect(closeModal).toHaveBeenCalledWith(payload));
  });

  it('enters submitting synchronously and ignores a second submit during provider validation', async () => {
    const providerValidation = deferred<any>();
    vi.spyOn(ApplicationWriter, 'createApplication').mockResolvedValue({ id: 'single-submit' } as any);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue({ id: 'single-submit' } as any);
    const { container } = await renderModal({ name: 'myapp' });
    fireEvent.change(screen.getByLabelText(/owner email/i), { target: { value: 'owner@example.com' } });
    (ApplicationNameValidator.validate as Mock).mockClear();
    (ApplicationNameValidator.validate as Mock).mockReturnValue(providerValidation.promise);

    fireEvent.submit(container.querySelector('form')!);
    fireEvent.submit(container.querySelector('form')!);
    expect(screen.getByRole('button', { name: /creating/i })).toBeDisabled();
    expect(ApplicationNameValidator.validate).toHaveBeenCalledTimes(1);

    providerValidation.resolve({ errors: [], warnings: [] });
    await waitFor(() => expect(ApplicationWriter.createApplication).toHaveBeenCalledTimes(1));
  });

  it('submits the application and providers snapshotted before async validation', async () => {
    const providerValidation = deferred<any>();
    vi.spyOn(ApplicationWriter, 'createApplication').mockResolvedValue({ id: 'snapshot' } as any);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue({ id: 'snapshot' } as any);
    const { container } = await renderModal({ name: 'original' });
    fireEvent.change(screen.getByLabelText(/owner email/i), { target: { value: 'owner@example.com' } });
    fireEvent.change(screen.getByLabelText(/description/i), { target: { value: 'validated draft' } });
    (ApplicationNameValidator.validate as Mock).mockReturnValue(providerValidation.promise);

    fireEvent.submit(container.querySelector('form')!);
    fireEvent.change(screen.getByLabelText(/name/i), { target: { value: 'newer' } });
    fireEvent.change(screen.getByLabelText(/description/i), { target: { value: 'newer unvalidated draft' } });
    providerValidation.resolve({ errors: [], warnings: [] });

    await waitFor(() => expect(ApplicationWriter.createApplication).toHaveBeenCalled());
    expect((ApplicationWriter.createApplication as Mock).mock.lastCall[0]).toEqual(
      expect.objectContaining({ name: 'original', cloudProviders: ['aws'], description: 'validated draft' }),
    );
  });

  it('restores submission after provider validation fails so the user can retry', async () => {
    const failedValidation = deferred<any>();
    vi.spyOn(ApplicationWriter, 'createApplication').mockResolvedValue({ id: 'retry' } as any);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue({ id: 'retry' } as any);
    await renderModal({ name: 'myapp' });
    fireEvent.change(screen.getByLabelText(/owner email/i), { target: { value: 'owner@example.com' } });
    (ApplicationNameValidator.validate as Mock).mockReturnValue(failedValidation.promise);

    submit();
    failedValidation.resolve({ errors: [{ cloudProvider: 'aws', message: 'invalid name' }], warnings: [] });
    await screen.findByText(/invalid name/i);
    expect(ApplicationWriter.createApplication).not.toHaveBeenCalled();

    (ApplicationNameValidator.validate as Mock).mockResolvedValue({ errors: [], warnings: [] });
    fireEvent.change(screen.getByLabelText(/name/i), { target: { value: 'myapp2' } });
    await waitFor(() => expect(screen.getByRole('button', { name: /^create$/i })).toBeEnabled());
    submit();
    await waitFor(() => expect(ApplicationWriter.createApplication).toHaveBeenCalledTimes(1));
  });

  it('restores submission after provider validation rejects so the user can retry', async () => {
    const rejectedValidation = deferred<any>();
    vi.spyOn(ApplicationWriter, 'createApplication').mockResolvedValue({ id: 'retry-rejection' } as any);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue({ id: 'retry-rejection' } as any);
    await renderModal({ name: 'myapp' });
    fireEvent.change(screen.getByLabelText(/owner email/i), { target: { value: 'owner@example.com' } });
    (ApplicationNameValidator.validate as Mock).mockReturnValue(rejectedValidation.promise);

    submit();
    rejectedValidation.reject(new Error('provider validation unavailable'));
    await screen.findByText('Could not validate application. Please try again.');
    expect(ApplicationWriter.createApplication).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /^create$/i })).toBeEnabled();

    (ApplicationNameValidator.validate as Mock).mockResolvedValue({ errors: [], warnings: [] });
    submit();
    await waitFor(() => expect(ApplicationWriter.createApplication).toHaveBeenCalledTimes(1));
  });

  it('shows retryable writer and task errors without closing', async () => {
    const closeModal = vi.fn();
    vi.spyOn(ApplicationWriter, 'createApplication')
      .mockRejectedValueOnce(new Error('writer failed'))
      .mockResolvedValueOnce({ id: '2' } as any);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockRejectedValue({ failureMessage: 'task failed' });
    await renderModal({ name: 'myapp', closeModal });
    fireEvent.change(screen.getByLabelText(/owner email/i), { target: { value: 'owner@example.com' } });

    submit();
    await screen.findByText('Could not create application');
    submit();
    await screen.findByText('Could not create application: task failed');

    expect(screen.getByRole('button', { name: /^create$/i })).toBeEnabled();
    expect(closeModal).not.toHaveBeenCalled();
  });

  it('dismisses on cancel and ignores late async completion after unmount', async () => {
    const finishTask = deferred<any>();
    const closeModal = vi.fn();
    const dismissModal = vi.fn();
    vi.spyOn(ApplicationWriter, 'createApplication').mockResolvedValue({ id: '3' } as any);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockReturnValue(finishTask.promise as any);
    const { unmount } = await renderModal({ name: 'myapp', closeModal, dismissModal });
    fireEvent.change(screen.getByLabelText(/owner email/i), { target: { value: 'owner@example.com' } });

    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(dismissModal).toHaveBeenCalledWith('cancel');
    submit();
    await waitFor(() => expect(TaskReader.waitUntilTaskCompletes).toHaveBeenCalled());
    unmount();
    finishTask.resolve({ id: '3' });
    await Promise.resolve();

    expect(closeModal).not.toHaveBeenCalled();
  });
});
