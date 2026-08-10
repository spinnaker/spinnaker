import { cloneDeep } from 'lodash';
import React from 'react';
import { Button, Modal } from 'react-bootstrap';

import { ApplicationProviderFields } from './ApplicationProviderFields';
import type { IPermissions } from './PermissionsConfigurer';
import { PermissionsConfigurer } from './PermissionsConfigurer';
import { PlatformHealthOverride } from './PlatformHealthOverride';
import { AccountService } from '../../account/AccountService';
import { SETTINGS } from '../../config/settings';
import { HelpField } from '../../help/HelpField';
import { PagerDutySelectField } from '../../pagerDuty/PagerDutySelectField';
import type { IModalComponentProps } from '../../presentation';
import { ReactModal, ReactSelectInput } from '../../presentation';
import type { IApplicationSummary } from '../service/ApplicationReader';
import { ApplicationReader } from '../service/ApplicationReader';
import type { IApplicationAttributes } from '../service/ApplicationWriter';
import { ApplicationWriter } from '../service/ApplicationWriter';
import SlackChannelSelector from '../../slack/SlackChannelSelector';
import { TaskReader } from '../../task/task.read.service';
import { noop } from '../../utils';
import type {
  IApplicationNameValidationMessage,
  IApplicationNameValidationResult,
} from './validation/ApplicationNameValidator';
import { ApplicationNameValidator } from './validation/ApplicationNameValidator';

export interface ICreateApplicationValidationResult {
  errors: string[];
  warnings: string[];
}

export function validateCreateApplication(
  application: IApplicationAttributes,
  existingApplicationNames: string[] = [],
  platformHealthWarningAcknowledged = true,
  pagerDutyRequired = false,
): ICreateApplicationValidationResult {
  const errors: string[] = [];
  const name = (application.name || '').trim();
  const email = (application.email || '').trim();

  if (!name) {
    errors.push('Application name is required.');
  } else if (existingApplicationNames.some((candidate) => candidate.toLowerCase() === name.toLowerCase())) {
    errors.push('Application name must be unique.');
  }

  if (!email) {
    errors.push('Owner email is required.');
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    errors.push('Please enter a valid email address.');
  }

  if (pagerDutyRequired && !application.pdApiKey) {
    errors.push('PagerDuty service is required.');
  }

  if (application.repoSlug && application.repoSlug.includes('://')) {
    errors.push('Enter your source repository name (not the URL).');
  }

  if (
    application.instancePort !== null &&
    application.instancePort !== undefined &&
    application.instancePort !== '' &&
    (!Number.isInteger(Number(application.instancePort)) ||
      Number(application.instancePort) < 0 ||
      Number(application.instancePort) > 65535)
  ) {
    errors.push('Instance port must be an integer between 0 and 65535.');
  }

  const permissions = application.permissions as IPermissions;
  if (permissions) {
    const read = permissions.READ || [];
    const write = permissions.WRITE || [];
    if (read.includes(null) || write.includes(null)) {
      errors.push('Permissions cannot contain empty groups.');
    }
    if (read.length > 0 && write.length === 0) {
      errors.push('Permissions must include a write group when read groups are configured.');
    }
  }

  if (application.platformHealthOnlyShowOverride && !platformHealthWarningAcknowledged) {
    errors.push('Acknowledge the platform health override warning.');
  }

  return { errors, warnings: [] };
}

export interface ICreateApplicationModalProps extends IModalComponentProps {
  name?: string;
}

export interface ICreateApplicationModalState {
  application: IApplicationAttributes;
  applicationNames: string[];
  availableProviders: string[];
  errorMessages: string[];
  initializeFailed: boolean;
  initializing: boolean;
  platformHealthWarningAcknowledged: boolean;
  providerErrors: IApplicationNameValidationMessage[];
  providerWarnings: IApplicationNameValidationMessage[];
  submitting: boolean;
  validationErrors: string[];
}

function createDraft(name = ''): IApplicationAttributes {
  const application: IApplicationAttributes = {
    name,
    cloudProviders: [],
    email: '',
    enableRerunActiveExecutions: false,
    enableRestartRunningExecutions: false,
    instancePort: SETTINGS.defaultInstancePort ?? null,
    permissions: { READ: [], EXECUTE: [], WRITE: [] },
    platformHealthOnly: false,
    platformHealthOnlyShowOverride: false,
  };

  if (SETTINGS.feature.chaosMonkey) {
    application.chaosMonkey = {
      enabled: Boolean(SETTINGS.newApplicationDefaults?.chaosMonkey),
      exceptions: [],
      grouping: 'cluster',
      meanTimeBetweenKillsInWorkDays: 2,
      minTimeBetweenKillsInWorkDays: 1,
      regionsAreIndependent: true,
    };
  }

  return application;
}

export class CreateApplicationModal extends React.Component<
  ICreateApplicationModalProps,
  ICreateApplicationModalState
> {
  public static defaultProps: Partial<ICreateApplicationModalProps> = {
    closeModal: noop,
    dismissModal: noop,
    name: '',
  };

  public static show(name = ''): Promise<IApplicationAttributes> {
    return ReactModal.show(CreateApplicationModal, { name }, { dialogClassName: 'modal-lg' });
  }

  public state: ICreateApplicationModalState = {
    application: createDraft(this.props.name),
    applicationNames: [],
    availableProviders: [],
    errorMessages: [],
    initializeFailed: false,
    initializing: true,
    platformHealthWarningAcknowledged: true,
    providerErrors: [],
    providerWarnings: [],
    submitting: false,
    validationErrors: [],
  };

  private mounted = false;
  private submissionInProgress = false;
  private validationSequence = 0;

  public componentDidMount(): void {
    this.mounted = true;
    Promise.all([
      Promise.resolve(ApplicationReader.listApplications()),
      Promise.resolve(AccountService.listProviders()),
    ])
      .then(([applications, availableProviders]: [IApplicationSummary[], string[]]) => {
        if (!this.mounted) {
          return;
        }
        this.setState(
          {
            applicationNames: applications.map(({ name }) => name),
            availableProviders,
            initializing: false,
          },
          this.validateProviderName,
        );
      })
      .catch(() => {
        if (this.mounted) {
          this.setState({ initializeFailed: true, initializing: false });
        }
      });
  }

  public componentWillUnmount(): void {
    this.mounted = false;
    this.validationSequence += 1;
  }

  private validateProviderName = async (
    application = this.state.application,
  ): Promise<IApplicationNameValidationResult> => {
    const sequence = ++this.validationSequence;
    const result = await ApplicationNameValidator.validate(application.name || '', application.cloudProviders || []);
    if (this.mounted && sequence === this.validationSequence) {
      this.setState({ providerErrors: result.errors, providerWarnings: result.warnings });
    }
    return result;
  };

  private updateApplication = (field: string, value: any): void => {
    const application = cloneDeep(this.state.application);
    application[field] = value;
    const nextState: Partial<ICreateApplicationModalState> = { application };
    if (field === 'platformHealthOnlyShowOverride') {
      nextState.platformHealthWarningAcknowledged = !value;
    }
    this.setState(nextState as Pick<ICreateApplicationModalState, keyof ICreateApplicationModalState>, () => {
      if (field === 'name' || field === 'cloudProviders') {
        this.validateProviderName();
      }
    });
  };

  private updateApplicationDraft = (application: IApplicationAttributes): void => this.setState({ application });

  private submit = async (): Promise<void> => {
    if (this.submissionInProgress) {
      return;
    }
    this.submissionInProgress = true;

    const application = cloneDeep(this.state.application);
    const applicationNames = [...this.state.applicationNames];
    const availableProviders = [...this.state.availableProviders];
    const platformHealthWarningAcknowledged = this.state.platformHealthWarningAcknowledged;
    if (availableProviders.length === 1) {
      application.cloudProviders = [availableProviders[0]];
    }
    this.setState({ errorMessages: [], submitting: true, validationErrors: [] });

    const validation = validateCreateApplication(
      application,
      applicationNames,
      platformHealthWarningAcknowledged,
      Boolean(SETTINGS.feature.pagerDuty && SETTINGS.pagerDuty?.required),
    );
    let providerValidation: IApplicationNameValidationResult;
    try {
      providerValidation = await this.validateProviderName(application);
    } catch (_error) {
      this.submissionInProgress = false;
      if (this.mounted) {
        this.setState({
          errorMessages: ['Could not validate application. Please try again.'],
          submitting: false,
        });
      }
      return;
    }
    if (!this.mounted) {
      return;
    }
    if (validation.errors.length || providerValidation.errors.length) {
      this.submissionInProgress = false;
      this.setState({
        providerErrors: providerValidation.errors,
        submitting: false,
        validationErrors: validation.errors,
      });
      return;
    }

    application.name = application.name.trim().toLowerCase();
    application.email = application.email.trim();

    let task: any;
    try {
      task = await ApplicationWriter.createApplication(application);
    } catch (_error) {
      if (this.mounted) {
        this.submissionInProgress = false;
        this.setState({ errorMessages: ['Could not create application'], submitting: false });
      }
      return;
    }

    try {
      await TaskReader.waitUntilTaskCompletes(task);
      if (this.mounted) {
        this.props.closeModal(application);
      }
    } catch (failedTask) {
      if (this.mounted) {
        const failureMessage = (failedTask as any)?.failureMessage || task?.failureMessage;
        this.submissionInProgress = false;
        this.setState({
          errorMessages: [`Could not create application${failureMessage ? `: ${failureMessage}` : ''}`],
          submitting: false,
        });
      }
    }
  };

  private renderField(
    label: string,
    field: string,
    options: { dataPurpose?: string; required?: boolean; type?: string } = {},
  ): React.ReactNode {
    return (
      <div className="form-group row">
        <label className="col-sm-3 sm-label-right" htmlFor={field}>
          {label}
          {options.required ? ' *' : ''}
        </label>
        <div className="col-sm-9">
          <input
            className="form-control input-sm"
            data-purpose={options.dataPurpose}
            id={field}
            name={field}
            required={options.required}
            type={options.type || 'text'}
            value={this.state.application[field] ?? ''}
            onChange={(event) => this.updateApplication(field, event.target.value)}
          />
        </div>
      </div>
    );
  }

  private renderCheckbox(label: string, field: string, helpKey?: string): React.ReactNode {
    return (
      <label style={{ display: 'block' }}>
        <input
          checked={Boolean(this.state.application[field])}
          type="checkbox"
          onChange={(event) => this.updateApplication(field, event.target.checked)}
        />{' '}
        {label} {helpKey && <HelpField id={helpKey} />}
      </label>
    );
  }

  public render(): React.ReactNode {
    const { application } = this.state;
    const gitSources = SETTINGS.gitSources || ['stash', 'github', 'bitbucket', 'gitlab'];
    const createDisabled =
      this.state.submitting ||
      this.state.providerErrors.length > 0 ||
      validateCreateApplication(
        application,
        this.state.applicationNames,
        this.state.platformHealthWarningAcknowledged,
        Boolean(SETTINGS.feature.pagerDuty && SETTINGS.pagerDuty?.required),
      ).errors.length > 0;
    const messages = this.state.validationErrors.concat(
      this.state.providerErrors.map(({ cloudProvider, message }) =>
        cloudProvider ? `${cloudProvider}: ${message}` : message,
      ),
      this.state.errorMessages,
    );

    return (
      <div className="modal-page">
        <Modal.Header>
          <Modal.Title>New Application</Modal.Title>
        </Modal.Header>
        {this.state.initializing && <div className="text-center modal-body">Loading...</div>}
        {this.state.initializeFailed && (
          <div className="modal-body alert alert-danger">
            Error initializing dialog. Check that your gate endpoint is accessible. Further information on
            troubleshooting this error is available <a href="https://www.spinnaker.io/setup/quickstart/faq/">here</a>.
          </div>
        )}
        {!this.state.initializing && !this.state.initializeFailed && (
          <form
            className="container-fluid"
            noValidate={true}
            onSubmit={(event) => {
              event.preventDefault();
              this.submit();
            }}
          >
            <Modal.Body>
              {this.renderField('Name', 'name', { dataPurpose: 'application-name', required: true })}
              {this.renderField('Owner Email', 'email', {
                dataPurpose: 'application-email',
                required: true,
                type: 'email',
              })}
              <div className="form-group row">
                <label className="col-sm-3 sm-label-right" htmlFor="repoType">
                  Repo Type
                </label>
                <div className="col-sm-9">
                  <select
                    className="form-control input-sm"
                    id="repoType"
                    value={application.repoType || ''}
                    onChange={(event) => this.updateApplication('repoType', event.target.value || null)}
                  >
                    <option value="">Select Repo Type</option>
                    {gitSources.map((source) => (
                      <option key={source} value={source}>
                        {source}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              {application.repoType && this.renderField('Repo Project', 'repoProjectKey')}
              {application.repoType && this.renderField('Repo Name', 'repoSlug')}
              {SETTINGS.feature.chaosMonkey && (
                <div className="form-group row">
                  <div className="col-sm-3 sm-label-right">
                    Chaos Monkey <HelpField id="application.chaos.enabled" />
                  </div>
                  <div className="col-sm-9 checkbox">
                    <label>
                      <input
                        checked={Boolean(application.chaosMonkey?.enabled)}
                        data-purpose="chaos-monkey-enabled"
                        type="checkbox"
                        onChange={(event) => {
                          const chaosMonkey = cloneDeep(application.chaosMonkey);
                          chaosMonkey.enabled = event.target.checked;
                          this.updateApplication('chaosMonkey', chaosMonkey);
                        }}
                      />{' '}
                      Enabled
                    </label>
                  </div>
                </div>
              )}
              {SETTINGS.feature.pagerDuty && (
                <PagerDutySelectField
                  value={application.pdApiKey || null}
                  onChange={(pdApiKey) => this.updateApplication('pdApiKey', pdApiKey)}
                />
              )}
              {SETTINGS.feature.slack && (
                <SlackChannelSelector
                  channel={application.slackChannel || null}
                  callback={(field, value) => this.updateApplication(field, value)}
                />
              )}
              <div className="form-group row">
                <label className="col-sm-3 sm-label-right" htmlFor="description">
                  Description
                </label>
                <div className="col-sm-9">
                  <textarea
                    className="form-control input-sm"
                    data-purpose="application-description"
                    id="description"
                    value={application.description || ''}
                    onChange={(event) => this.updateApplication('description', event.target.value)}
                  />
                </div>
              </div>
              <div className="form-group row">
                <label className="col-sm-3 sm-label-right">Cloud Providers</label>
                <div className="col-sm-9">
                  <ReactSelectInput
                    inputClassName="form-control input-sm"
                    multi={true}
                    name="cloudProviders"
                    stringOptions={this.state.availableProviders}
                    value={application.cloudProviders || []}
                    onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
                      this.updateApplication('cloudProviders', event.target.value || [])
                    }
                  />
                </div>
              </div>
              <ApplicationProviderFields
                application={application}
                availableProviders={this.state.availableProviders}
                selectedProviders={application.cloudProviders || []}
                onChange={this.updateApplicationDraft}
              />
              <div className="form-group row">
                <div className="col-sm-3 sm-label-right">Instance Health</div>
                <div className="col-sm-9">
                  <PlatformHealthOverride
                    interestingHealthProviderNames={application.platformHealthOnly ? ['cloud provider'] : []}
                    platformHealthType="cloud provider"
                    onChange={(names) => this.updateApplication('platformHealthOnly', Boolean(names?.length))}
                  />
                  {this.renderCheckbox(
                    'Show health override option for each operation',
                    'platformHealthOnlyShowOverride',
                    'application.showPlatformHealthOverride',
                  )}
                </div>
              </div>
              {application.platformHealthOnlyShowOverride && !this.state.platformHealthWarningAcknowledged && (
                <div className="alert alert-warning" data-purpose="platform-health-warning">
                  <p>
                    Simply enabling the "Consider only cloud provider health when executing tasks" option is usually
                    sufficient. Pipelines require manual updating if this setting is disabled later.
                  </p>
                  <Button
                    bsSize="small"
                    data-purpose="acknowledge-platform-health-warning"
                    onClick={() => this.setState({ platformHealthWarningAcknowledged: true })}
                  >
                    Okay
                  </Button>
                </div>
              )}
              <div className="form-group row">
                <label className="col-sm-3 sm-label-right" htmlFor="instancePort">
                  Instance Port <HelpField id="application.instance.port" />
                </label>
                <div className="col-sm-3">
                  <input
                    className="form-control input-sm"
                    id="instancePort"
                    max={65535}
                    min={0}
                    name="instancePort"
                    type="number"
                    value={application.instancePort ?? ''}
                    onChange={(event) =>
                      this.updateApplication(
                        'instancePort',
                        event.target.value === '' ? null : Number(event.target.value),
                      )
                    }
                  />
                </div>
              </div>
              <div className="form-group row">
                <div className="col-sm-3 sm-label-right">Pipeline Behavior</div>
                <div className="col-sm-9 checkbox">
                  {this.renderCheckbox(
                    'Enable restarting running pipelines',
                    'enableRestartRunningExecutions',
                    'application.enableRestartRunningExecutions',
                  )}
                  {this.renderCheckbox(
                    'Enable re-run button on active pipelines',
                    'enableRerunActiveExecutions',
                    'application.enableRerunActiveExecutions',
                  )}
                </div>
              </div>
              {SETTINGS.feature.fiatEnabled && (
                <div className="form-group row">
                  <div className="col-sm-3 sm-label-right">
                    Permissions <HelpField id="application.permissions" />
                  </div>
                  <div className="col-sm-9">
                    <PermissionsConfigurer
                      permissions={application.permissions}
                      requiredGroupMembership={application.requiredGroupMembership}
                      onPermissionsChange={(permissions) => this.updateApplication('permissions', permissions)}
                    />
                  </div>
                </div>
              )}
              {this.state.providerWarnings.map(({ cloudProvider, message }, index) => (
                <div className="alert alert-warning" key={`${cloudProvider || 'provider'}-${index}`}>
                  {cloudProvider && <strong>{cloudProvider}: </strong>}
                  {message}
                </div>
              ))}
              {messages.length > 0 && (
                <div className="alert alert-danger" data-purpose="create-application-errors">
                  {messages.map((message, index) => (
                    <div key={`${message}-${index}`}>{message}</div>
                  ))}
                </div>
              )}
            </Modal.Body>
            <Modal.Footer>
              <Button
                data-purpose="cancel-create-application"
                disabled={this.state.submitting}
                onClick={() => this.props.dismissModal('cancel')}
              >
                Cancel
              </Button>
              <Button bsStyle="primary" data-purpose="create-application" disabled={createDisabled} type="submit">
                {this.state.submitting ? 'Creating...' : 'Create'}
              </Button>
            </Modal.Footer>
          </form>
        )}
      </div>
    );
  }
}
