import * as React from 'react';

import Select, { Option } from 'react-select';
import { FormikErrors, FormikProps } from 'formik';

import { IArtifactAccount, IWizardPageProps, wizardPage, ValidationMessage, HelpField } from '@spinnaker/core';

import {
  ICloudFoundryCreateServerGroupCommand,
  ICloudFoundryManifestArtifactSource,
  ICloudFoundryManifestDirectSource,
  ICloudFoundryManifestSource,
  ICloudFoundryManifestTriggerSource,
} from '../../serverGroupConfigurationModel.cf';

export interface ICloudFoundryServerGroupConfigurationSettingsProps {
  artifactAccounts: IArtifactAccount[];
  manifest?: any;
}

interface ICloudFoundryServerGroupConfigurationSettingsState {}

function isManifestArtifactSource(
  manifest: ICloudFoundryManifestSource,
): manifest is { type: string } & ICloudFoundryManifestArtifactSource {
  return manifest.type === 'artifact';
}

function isManifestDirectSource(
  manifest: ICloudFoundryManifestSource,
): manifest is { type: string } & ICloudFoundryManifestDirectSource {
  return manifest.type === 'direct';
}

function isManifestTriggerSource(
  manifest: ICloudFoundryManifestSource,
): manifest is { type: string } & ICloudFoundryManifestTriggerSource {
  return manifest.type === 'trigger';
}

class ConfigurationSettingsImpl extends React.Component<
  ICloudFoundryServerGroupConfigurationSettingsProps &
    IWizardPageProps &
    FormikProps<ICloudFoundryCreateServerGroupCommand>,
  ICloudFoundryServerGroupConfigurationSettingsState
> {
  public static get LABEL() {
    return 'Configuration';
  }

  constructor(
    props: ICloudFoundryServerGroupConfigurationSettingsProps &
      IWizardPageProps &
      FormikProps<ICloudFoundryCreateServerGroupCommand>,
  ) {
    super(props);
    this.state = {};
  }

  private startApplicationUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const startApplication = event.target.checked;
    this.props.values.startApplication = startApplication;
    this.props.setFieldValue('startApplication', startApplication);
  };

  private memoryUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const { manifest } = this.props.values;
    if (isManifestDirectSource(manifest)) {
      manifest.memory = event.target.value;
      this.props.setFieldValue('manifest.memory', event.target.value);
    }
  };

  private diskQuotaUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const { manifest } = this.props.values;
    if (isManifestDirectSource(manifest)) {
      manifest.diskQuota = event.target.value;
      this.props.setFieldValue('manifest.diskQuota', event.target.value);
    }
  };

  private instancesUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const { manifest } = this.props.values;
    if (isManifestDirectSource(manifest)) {
      manifest.instances = Number(event.target.value);
      this.props.setFieldValue('manifest.instances', event.target.value);
      this.capacityUpdated(event.target.value);
    }
  };

  private buildpackUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const { manifest } = this.props.values;
    if (isManifestDirectSource(manifest)) {
      manifest.buildpack = event.target.value;
      this.props.setFieldValue('manifest.buildpack', event.target.value);
    }
  };

  private manifestTypeUpdated = (type: string): void => {
    switch (type) {
      case 'artifact':
        this.props.values.manifest = { type: 'artifact', reference: '', account: '' };
        this.capacityUpdated('1');
        break;
      case 'trigger':
        this.props.values.manifest = { type: 'trigger', pattern: '', account: '' };
        this.capacityUpdated('1');
        break;
      case 'direct':
        this.props.values.manifest = {
          type: 'direct',
          memory: '1024M',
          diskQuota: '1024M',
          instances: 1,
          buildpack: undefined,
          routes: [],
          env: [],
          services: [],
        };
        break;
    }
    this.props.setFieldValue('manifest', this.props.values.manifest);
  };

  private routeUpdated = (index: number, event: React.ChangeEvent<HTMLInputElement>): void => {
    const { manifest } = this.props.values;
    if (isManifestDirectSource(manifest)) {
      manifest.routes[index] = event.target.value;
      this.props.setFieldValue('manifest.routes', manifest.routes);
    }
  };

  private addRoutesVariable = (): void => {
    const { manifest } = this.props.values;
    if (isManifestDirectSource(manifest)) {
      if (manifest.routes === undefined) {
        manifest.routes = [];
      }
      manifest.routes.push('');
      this.props.setFieldValue('manifest.routes', manifest.routes);
    }
  };

  private removeRoutesVariable = (index: number): void => {
    const { manifest } = this.props.values;
    if (isManifestDirectSource(manifest)) {
      manifest.routes.splice(index, 1);
      this.props.setFieldValue('manifest.routes', manifest.routes);
    }
  };

  private addEnvironmentVariable = (): void => {
    const { manifest } = this.props.values;
    if (isManifestDirectSource(manifest)) {
      if (manifest.env === undefined) {
        manifest.env = [];
      }
      manifest.env.push({ key: '', value: '' });
      this.props.setFieldValue('manifest.env', manifest.env);
    }
  };

  private removeEnvironmentVariable = (index: number): void => {
    const { manifest } = this.props.values;
    if (isManifestDirectSource(manifest)) {
      manifest.env.splice(index, 1);
      this.props.setFieldValue('manifest.env', manifest.env);
    }
  };

  private environmentKeyUpdated = (index: number, event: React.ChangeEvent<HTMLInputElement>): void => {
    const { manifest } = this.props.values;
    if (isManifestDirectSource(manifest)) {
      manifest.env[index].key = event.target.value;
      this.props.setFieldValue('manifest.env', manifest.env);
    }
  };

  private environmentValueUpdated = (index: number, event: React.ChangeEvent<HTMLInputElement>): void => {
    const { manifest } = this.props.values;
    if (isManifestDirectSource(manifest)) {
      manifest.env[index].value = event.target.value;
      this.props.setFieldValue('manifest.env', manifest.env);
    }
  };

  private serviceUpdated = (index: number, event: React.ChangeEvent<HTMLInputElement>): void => {
    const { manifest } = this.props.values;
    if (isManifestDirectSource(manifest)) {
      manifest.services[index] = event.target.value;
      this.props.setFieldValue('manifest.services', manifest.services);
    }
  };

  private addServicesVariable = (): void => {
    const { manifest } = this.props.values;
    if (isManifestDirectSource(manifest)) {
      if (manifest.services === undefined) {
        manifest.services = [];
      }
      manifest.services.push('');
      this.props.setFieldValue('manifest.services', manifest.services);
    }
  };

  private removeServicesVariable = (index: number): void => {
    const { manifest } = this.props.values;
    if (isManifestDirectSource(manifest)) {
      manifest.services.splice(index, 1);
      this.props.setFieldValue('manifest.services', manifest.services);
    }
  };

  private artifactAccountUpdated = (option: Option<string>): void => {
    const { manifest } = this.props.values;
    if (isManifestArtifactSource(manifest) || isManifestTriggerSource(manifest)) {
      manifest.account = option.value;
      this.props.setFieldValue('manifest.account', option.value);
    }
  };

  private artifactReferenceUpdated = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const reference = event.target.value;
    const { manifest } = this.props.values;
    if (isManifestArtifactSource(manifest)) {
      manifest.reference = reference;
      this.props.setFieldValue('manifest.reference', reference);
    }
  };

  private manifestPatternUpdater = (event: React.ChangeEvent<HTMLInputElement>): void => {
    const pattern = event.target.value;
    const { manifest } = this.props.values;
    if (isManifestTriggerSource(manifest)) {
      manifest.pattern = pattern;
      this.props.setFieldValue('manifest.pattern', pattern);
    }
  };

  private capacityUpdated = (capacity: string): void => {
    this.props.setFieldValue('capacity.min', capacity);
    this.props.setFieldValue('capacity.max', capacity);
    this.props.setFieldValue('capacity.desired', capacity);
  };

  private directConfiguration = (manifest: ICloudFoundryManifestSource): JSX.Element => {
    const {
      routeUpdated,
      removeRoutesVariable,
      addRoutesVariable,
      addEnvironmentVariable,
      removeEnvironmentVariable,
      environmentKeyUpdated,
      environmentValueUpdated,
      serviceUpdated,
      addServicesVariable,
      removeServicesVariable,
    } = this;
    const { errors } = this.props;
    if (isManifestDirectSource(manifest)) {
      return (
        <div>
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Memory</div>
            <div className="col-md-7">
              <input type="text" value={manifest.memory} onChange={this.memoryUpdated} />
            </div>
          </div>
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Disk Quota</div>
            <div className="col-md-7">
              <input type="text" value={manifest.diskQuota} onChange={this.diskQuotaUpdated} />
            </div>
          </div>
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Instances</div>
            <div className="col-md-7">
              <input type="number" value={manifest.instances} onChange={this.instancesUpdated} />
            </div>
          </div>
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Buildpack</div>
            <div className="col-md-7">
              <input type="text" value={manifest.buildpack} onChange={this.buildpackUpdated} />
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-12">
              <b>Routes</b>&nbsp;<HelpField id="cf.serverGroup.routes" />
              <table className="table table-condensed packed metadata">
                <tbody>
                  {manifest.routes &&
                    manifest.routes.map(function(route, index) {
                      return (
                        <tr key={index}>
                          <td>
                            <input
                              className="form-control input-sm"
                              value={route}
                              type="text"
                              ng-model="command.services[$index]"
                              required={true}
                              onChange={event => routeUpdated(index, event)}
                            />
                          </td>
                          <td>
                            <a className="btn btn-link sm-label" onClick={() => removeRoutesVariable(index)}>
                              <span className="glyphicon glyphicon-trash" />
                            </a>
                          </td>
                        </tr>
                      );
                    })}
                </tbody>
                <tfoot>
                  <tr>
                    <td colSpan={1}>
                      <button type="button" className="add-new col-md-12" onClick={addRoutesVariable}>
                        <span className="glyphicon glyphicon-plus-sign" /> Add New Route
                      </button>
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-12">
              <b>Environment Variables</b>
              <table className="table table-condensed packed tags">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Value</th>
                  </tr>
                </thead>
                <tbody>
                  {manifest.env &&
                    manifest.env.map(function(env, index) {
                      return (
                        <tr key={index}>
                          <td>
                            <input
                              className="form-control input-sm"
                              type="text"
                              value={env.key}
                              required={true}
                              onChange={event => environmentKeyUpdated(index, event)}
                            />
                          </td>
                          <td>
                            <input
                              className="form-control input-sm"
                              type="text"
                              value={env.value}
                              required={true}
                              onChange={event => environmentValueUpdated(index, event)}
                            />
                          </td>
                          <td>
                            <a className="btn btn-link sm-label">
                              <span
                                className="glyphicon glyphicon-trash"
                                onClick={() => removeEnvironmentVariable(index)}
                              />
                            </a>
                          </td>
                        </tr>
                      );
                    })}
                </tbody>
                <tfoot>
                  <tr>
                    <td colSpan={2}>
                      <button type="button" className="add-new col-md-12" onClick={addEnvironmentVariable}>
                        <span className="glyphicon glyphicon-plus-sign" /> Add New Environment Variable
                      </button>
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-12">
              <b>Services</b>
              <table className="table table-condensed packed metadata">
                <tbody>
                  {manifest.services &&
                    manifest.services.map(function(service, index) {
                      return (
                        <tr key={index}>
                          <td>
                            <input
                              className="form-control input-sm"
                              value={service}
                              type="text"
                              ng-model="command.services[$index]"
                              required={true}
                              onChange={event => serviceUpdated(index, event)}
                            />
                          </td>
                          <td>
                            <a className="btn btn-link sm-label" onClick={() => removeServicesVariable(index)}>
                              <span className="glyphicon glyphicon-trash" />
                            </a>
                          </td>
                        </tr>
                      );
                    })}
                </tbody>
                <tfoot>
                  <tr>
                    <td colSpan={1}>
                      <button type="button" className="add-new col-md-12" onClick={addServicesVariable}>
                        <span className="glyphicon glyphicon-plus-sign" /> Add New Service
                      </button>
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>
          {errors.manifest &&
            errors.manifest.memory && (
              <div className="wizard-pod-row-errors">
                <ValidationMessage message={errors.manifest.memory} type={'error'} />
              </div>
            )}
          {errors.manifest &&
            errors.manifest.diskQuota && (
              <div className="wizard-pod-row-errors">
                <ValidationMessage message={errors.manifest.diskQuota} type={'error'} />
              </div>
            )}
          {errors.manifest &&
            errors.manifest.routes && (
              <div className="wizard-pod-row-errors">
                <ValidationMessage message={errors.manifest.routes} type={'error'} />
              </div>
            )}
          {errors.manifest &&
            errors.manifest.env && (
              <div className="wizard-pod-row-errors">
                <ValidationMessage message={errors.manifest.env} type={'error'} />
              </div>
            )}
          {errors.manifest &&
            errors.manifest.services && (
              <div className="wizard-pod-row-errors">
                <ValidationMessage message={errors.manifest.services} type={'error'} />
              </div>
            )}
        </div>
      );
    } else {
      return <div>Cannot display direct configuration</div>;
    }
  };

  private triggerConfiguration = (manifest: ICloudFoundryManifestSource): JSX.Element => {
    const { artifactAccounts, errors } = this.props;
    return (
      <div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Manifest Pattern</div>
          <div className="col-md-7">
            <input
              className="form-control input-sm no-spel"
              value={isManifestTriggerSource(manifest) && manifest.pattern}
              onChange={this.manifestPatternUpdater}
            />
          </div>
        </div>
        <div className="form-group row">
          <label className="col-md-3 sm-label-right">
            Artifact Account <HelpField id="cf.manifest.trigger.account" />
          </label>
          <div className="col-md-7">
            <Select
              options={
                artifactAccounts &&
                artifactAccounts.map((account: IArtifactAccount) => ({
                  label: account.name,
                  value: account.name,
                }))
              }
              clearable={true}
              value={isManifestTriggerSource(manifest) && manifest.account}
              onChange={this.artifactAccountUpdated}
            />
          </div>
        </div>
        {errors.manifest &&
          errors.manifest.account && (
            <div className="wizard-pod-row-errors">
              <ValidationMessage message={errors.manifest.account} type={'error'} />
            </div>
          )}
        {errors.manifest &&
          errors.manifest.pattern && (
            <div className="wizard-pod-row-errors">
              <ValidationMessage message={errors.manifest.pattern} type={'error'} />
            </div>
          )}
      </div>
    );
  };

  private artifactConfiguration = (manifest: ICloudFoundryManifestSource): JSX.Element => {
    const { artifactAccounts, errors } = this.props;
    if (isManifestArtifactSource(manifest)) {
      return (
        <div>
          <div className="form-group row">
            <label className="col-md-3 sm-label-right">Artifact Account</label>
            <div className="col-md-7">
              <Select
                options={
                  artifactAccounts &&
                  artifactAccounts.map((account: IArtifactAccount) => ({
                    label: account.name,
                    value: account.name,
                  }))
                }
                clearable={false}
                onChange={this.artifactAccountUpdated}
                value={manifest.account}
              />
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-3 sm-label-right">Reference</div>
            <div className="col-md-7">
              <input
                type="text"
                required={true}
                className="form-control input-sm"
                onChange={this.artifactReferenceUpdated}
                value={manifest.reference}
              />
            </div>
          </div>
          {errors.manifest &&
            errors.manifest.account && (
              <div className="wizard-pod-row-errors">
                <ValidationMessage message={errors.manifest.account} type={'error'} />
              </div>
            )}
          {errors.manifest &&
            errors.manifest.reference && (
              <div className="wizard-pod-row-errors">
                <ValidationMessage message={errors.manifest.reference} type={'error'} />
              </div>
            )}
        </div>
      );
    } else {
      return <div>Cannot display artifact configuration</div>;
    }
  };

  public render(): JSX.Element {
    const { manifest } = this.props.values;
    manifest.type === 'direct' ? this.directConfiguration(manifest) : this.artifactConfiguration(manifest);
    let manifestInput;

    switch (manifest.type) {
      case 'direct':
        manifestInput = this.directConfiguration(manifest);
        break;
      case 'trigger':
        manifestInput = this.triggerConfiguration(manifest);
        break;
      default:
        manifestInput = this.artifactConfiguration(manifest);
    }
    return (
      <div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Start on creation <HelpField id="cf.serverGroup.startApplication" />
          </div>
          <div className="checkbox checkbox-inline">
            <input
              type="checkbox"
              checked={this.props.values.startApplication}
              onChange={this.startApplicationUpdated}
            />
          </div>
        </div>
        <div className="form-group row">
          <label className="col-md-3 sm-label-right">Source Type</label>
          <div className="col-md-7">
            {this.props.values.viewState.mode === 'pipeline' && (
              <div className="radio radio-inline">
                <label>
                  <input
                    type="radio"
                    value="trigger"
                    checked={manifest.type === 'trigger'}
                    onChange={() => this.manifestTypeUpdated('trigger')}
                  />{' '}
                  Trigger
                </label>
              </div>
            )}
            <div className="radio radio-inline">
              <label>
                <input
                  type="radio"
                  value="droplet"
                  checked={manifest.type === 'direct'}
                  onChange={() => this.manifestTypeUpdated('direct')}
                />{' '}
                Direct
              </label>
            </div>
            <div className="radio radio-inline">
              <label>
                <input
                  type="radio"
                  value="droplet"
                  checked={manifest.type === 'artifact'}
                  onChange={() => this.manifestTypeUpdated('artifact')}
                />{' '}
                Artifact
              </label>
            </div>
          </div>
        </div>
        {manifestInput}
      </div>
    );
  }

  public validate(
    values: ICloudFoundryServerGroupConfigurationSettingsProps,
  ): FormikErrors<ICloudFoundryCreateServerGroupCommand> {
    const errors = {} as FormikErrors<ICloudFoundryCreateServerGroupCommand>;
    const isStorageSize = (value: string) => /\d+[MG]/.test(value);
    if (isManifestDirectSource(values.manifest)) {
      if (!isStorageSize(values.manifest.memory)) {
        errors.manifest = errors.manifest || {};
        errors.manifest.memory = `Provide a size (e.g.: 256M, 1G)`;
      }
      if (!isStorageSize(values.manifest.diskQuota)) {
        errors.manifest = errors.manifest || {};
        errors.manifest.diskQuota = `Provide a size (e.g.: 256M, 1G)`;
      }
      if (values.manifest.routes) {
        values.manifest.routes.forEach(function(route) {
          if (!route) {
            errors.manifest = errors.manifest || {};
            errors.manifest.routes = `A route was not specified`;
          }
          const regex = /^([a-zA-Z0-9_-]+)\.([a-zA-Z0-9_.-]+)(\:[0-9]+)?([\/a-zA-Z0-9_-]+)?$/gm;
          if (route && regex.exec(route) === null) {
            errors.manifest = errors.manifest || {};
            errors.manifest.routes = `A route did not match the expected format (host.some.domain[:9999][/some/path]`;
          }
        });
      }
      if (values.manifest.services) {
        const existingServices: any = {};
        values.manifest.services.forEach(function(service) {
          if (!service) {
            errors.manifest = errors.manifest || {};
            errors.manifest.services = `A service was not specified`;
          }
          if (!!existingServices[service]) {
            errors.manifest = errors.manifest || {};
            errors.manifest.services = `Service "` + service + `" was duplicated`;
          }
          existingServices[service] = service;
        });
      }
      if (values.manifest.env) {
        const existingKeys: any = {};
        values.manifest.env.forEach(function(e) {
          if (!e.key || !e.value) {
            errors.manifest = errors.manifest || {};
            errors.manifest.env = `An environment variable was not set`;
          } else {
            const value = existingKeys[e.key];
            if (!value) {
              existingKeys[e.key] = e.value;
            } else {
              errors.manifest = errors.manifest || {};
              errors.manifest.env =
                `Duplicate environment variable: "` + e.key + `" set to "` + value + `" and "` + e.value + `"`;
            }
          }
        });
      }
    }
    if (isManifestArtifactSource(values.manifest)) {
      if (!values.manifest.account) {
        errors.manifest = errors.manifest || {};
        errors.manifest.account = `Account must be selected`;
      }
      if (!values.manifest.reference) {
        errors.manifest = errors.manifest || {};
        errors.manifest.reference = `Reference must be specified`;
      }
    }
    if (isManifestTriggerSource(values.manifest)) {
      if (!values.manifest.account) {
        errors.manifest = errors.manifest || {};
        errors.manifest.account = 'Account must be selected';
      }
      if (!values.manifest.pattern) {
        errors.manifest = errors.manifest || {};
        errors.manifest.pattern = 'Pattern must be specified';
      }
    }

    return errors;
  }
}

export const CloudFoundryServerGroupConfigurationSettings = wizardPage<
  ICloudFoundryServerGroupConfigurationSettingsProps
>(ConfigurationSettingsImpl);
