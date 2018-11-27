import * as React from 'react';

import Select, { Option } from 'react-select';

import { IArtifactAccount, IWizardPageProps, wizardPage, ValidationMessage, HelpField } from '@spinnaker/core';

import {
  ICloudFoundryCreateServerGroupCommand,
  ICloudFoundryManifestArtifactSource,
  ICloudFoundryManifestDirectSource,
  ICloudFoundryManifestSource,
  ICloudFoundryManifestTriggerSource,
} from '../../serverGroupConfigurationModel.cf';

import { Field, FieldArray } from 'formik';

export interface ICloudFoundryServerGroupConfigurationSettingsProps
  extends IWizardPageProps<ICloudFoundryCreateServerGroupCommand> {
  artifactAccounts: IArtifactAccount[];
  manifest?: any;
}

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

class ConfigurationSettingsImpl extends React.Component<ICloudFoundryServerGroupConfigurationSettingsProps> {
  public static LABEL = 'Configuration';

  private manifestTypeUpdated = (type: string): void => {
    switch (type) {
      case 'artifact':
        this.props.formik.values.manifest = { type: 'artifact', reference: '', account: '' };
        this.capacityUpdated('1');
        break;
      case 'trigger':
        this.props.formik.values.manifest = { type: 'trigger', pattern: '', account: '' };
        this.capacityUpdated('1');
        break;
      case 'direct':
        this.props.formik.values.manifest = {
          type: 'direct',
          memory: '1024M',
          diskQuota: '1024M',
          instances: 1,
          buildpack: undefined,
          healthCheckType: 'port',
          healthCheckHttpEndpoint: undefined,
          routes: [],
          environment: [],
          services: [],
        };
        break;
    }
    this.props.formik.setFieldValue('manifest', this.props.formik.values.manifest);
  };

  private artifactAccountUpdated = (option: Option<string>): void => {
    const { manifest } = this.props.formik.values;
    if (isManifestArtifactSource(manifest) || isManifestTriggerSource(manifest)) {
      manifest.account = option.value;
      this.props.formik.setFieldValue('manifest.account', option.value);
    }
  };

  private capacityUpdated = (capacity: string): void => {
    this.props.formik.setFieldValue('capacity.min', capacity);
    this.props.formik.setFieldValue('capacity.max', capacity);
    this.props.formik.setFieldValue('capacity.desired', capacity);
  };

  private healthCheckTypeUpdated = (healthCheckType: string): void => {
    this.props.formik.setFieldValue('manifest.healthCheckType', healthCheckType);
  };

  private directConfiguration = (manifest: ICloudFoundryManifestSource): JSX.Element => {
    const { errors } = this.props.formik as any;
    if (isManifestDirectSource(manifest)) {
      return (
        <div>
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Memory</div>
            <div className="col-md-7">
              <Field type="text" name="manifest.memory" />
            </div>
          </div>
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Disk Quota</div>
            <div className="col-md-7">
              <Field type="text" name="manifest.diskQuota" />
            </div>
          </div>
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Instances</div>
            <div className="col-md-7">
              <Field type="number" name="manifest.instances" />
            </div>
          </div>
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Buildpack</div>
            <div className="col-md-7">
              <Field type="text" name="manifest.buildpack" />
            </div>
          </div>

          <div>
            <div className="form-group row">
              <label className="col-md-3 sm-label-right">Health Check Type</label>
              <div className="col-md-7">
                <div className="radio radio-inline">
                  <label>
                    <input
                      type="radio"
                      value="port"
                      checked={manifest.healthCheckType === 'port'}
                      onChange={() => this.healthCheckTypeUpdated('port')}
                    />{' '}
                    port
                  </label>
                </div>
                <div className="radio radio-inline">
                  <label>
                    <input
                      type="radio"
                      value="http"
                      checked={manifest.healthCheckType === 'http'}
                      onChange={() => this.healthCheckTypeUpdated('http')}
                    />{' '}
                    HTTP
                  </label>
                </div>
                <div className="radio radio-inline">
                  <label>
                    <input
                      type="radio"
                      value="process"
                      checked={manifest.healthCheckType === 'process'}
                      onChange={() => this.healthCheckTypeUpdated('process')}
                    />{' '}
                    process
                  </label>
                </div>
              </div>
            </div>
          </div>
          {manifest.healthCheckType === 'http' && (
            <div className="form-group">
              <div className="col-md-3 sm-label-right">Health Check Endpoint</div>
              <div className="col-md-7">
                <Field type="text" name="manifest.healthCheckHttpEndpoint" />
              </div>
            </div>
          )}

          <div className="form-group">
            <div className="col-md-12">
              <b>Routes</b>
              &nbsp;
              <HelpField id="cf.serverGroup.routes" />
              <FieldArray
                name="manifest.routes"
                render={arrayHelpers => (
                  <table className="table table-condensed packed metadata">
                    <tbody>
                      {manifest.routes &&
                        manifest.routes.map((_, index) => (
                          <tr key={index}>
                            <td>
                              <Field
                                className="form-control input-sm"
                                name={`manifest.routes.${index}`}
                                type="text"
                                required={true}
                              />
                            </td>
                            <td>
                              <a className="btn btn-link sm-label" onClick={() => arrayHelpers.remove(index)}>
                                <span className="glyphicon glyphicon-trash" />
                              </a>
                            </td>
                          </tr>
                        ))}
                    </tbody>
                    <tfoot>
                      <tr>
                        <td colSpan={1}>
                          <button type="button" className="add-new col-md-12" onClick={() => arrayHelpers.push('')}>
                            <span className="glyphicon glyphicon-plus-sign" /> Add New Route
                          </button>
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                )}
              />
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-12">
              <b>Environment Variables</b>
              <FieldArray
                name="manifest.environment"
                render={arrayHelpers => (
                  <table className="table table-condensed packed tags">
                    <thead>
                      <tr>
                        <th>Name</th>
                        <th>Value</th>
                      </tr>
                    </thead>
                    <tbody>
                      {manifest.environment &&
                        manifest.environment.map(function(_, index) {
                          return (
                            <tr key={index}>
                              <td>
                                <Field
                                  className="form-control input-sm"
                                  type="text"
                                  name={`manifest.environment.${index}.key`}
                                  required={true}
                                />
                              </td>
                              <td>
                                <Field
                                  className="form-control input-sm"
                                  type="text"
                                  name={`manifest.environment.${index}.value`}
                                  required={true}
                                />
                              </td>
                              <td>
                                <a className="btn btn-link sm-label">
                                  <span
                                    className="glyphicon glyphicon-trash"
                                    onClick={() => arrayHelpers.remove(index)}
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
                          <button
                            type="button"
                            className="add-new col-md-12"
                            onClick={() => arrayHelpers.push({ key: '', value: '' })}
                          >
                            <span className="glyphicon glyphicon-plus-sign" /> Add New Environment Variable
                          </button>
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                )}
              />
            </div>
          </div>

          <div className="form-group">
            <div className="col-md-12">
              <b>Bind Services</b>
              <FieldArray
                name="manifest.services"
                render={arrayHelpers => (
                  <table className="table table-condensed packed metadata">
                    <tbody>
                      {manifest.services &&
                        manifest.services.map(function(_, index) {
                          return (
                            <tr key={index}>
                              <td>
                                <Field
                                  className="form-control input-sm"
                                  name={`manifest.services.${index}`}
                                  type="text"
                                  required={true}
                                />
                              </td>
                              <td>
                                <a className="btn btn-link sm-label" onClick={() => arrayHelpers.remove(index)}>
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
                          <button type="button" className="add-new col-md-12" onClick={() => arrayHelpers.push('')}>
                            <span className="glyphicon glyphicon-plus-sign" /> Bind Service
                          </button>
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                )}
              />
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
            errors.manifest.routes &&
            errors.manifest.routes.map((routeError: string) => (
              <div className="wizard-pod-row-errors">
                <ValidationMessage message={routeError} type={'error'} />
              </div>
            ))}
          {errors.manifest &&
            errors.manifest.environment &&
            errors.manifest.environment.map((environmentError: string) => (
              <div className="wizard-pod-row-errors">
                <ValidationMessage message={environmentError} type={'error'} />
              </div>
            ))}
          {errors.manifest &&
            errors.manifest.services &&
            errors.manifest.services.map((servicesError: string) => (
              <div className="wizard-pod-row-errors">
                <ValidationMessage message={servicesError} type={'error'} />
              </div>
            ))}
        </div>
      );
    } else {
      return <div>Cannot display direct configuration</div>;
    }
  };

  private triggerConfiguration = (manifest: ICloudFoundryManifestSource): JSX.Element => {
    const { artifactAccounts } = this.props;
    const { errors } = this.props.formik as any;

    return (
      <div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Manifest Pattern</div>
          <div className="col-md-7">
            <Field className="form-control input-sm no-spel" name="manifest.pattern" type="text" />
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
    const { artifactAccounts } = this.props;
    const { errors } = this.props.formik as any;

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
              <Field type="text" required={true} className="form-control input-sm" name="manifest.reference" />
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
    const { manifest } = this.props.formik.values;
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
        <div className="form-group row">
          <label className="col-md-3 sm-label-right">Source Type</label>
          <div className="col-md-7">
            {this.props.formik.values.viewState.mode === 'pipeline' && (
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
                  checked={manifest.type === 'artifact'}
                  onChange={() => this.manifestTypeUpdated('artifact')}
                />{' '}
                Artifact
              </label>
            </div>
            <div className="radio radio-inline">
              <label>
                <input
                  type="radio"
                  value="droplet"
                  checked={manifest.type === 'direct'}
                  onChange={() => this.manifestTypeUpdated('direct')}
                />{' '}
                Form
              </label>
            </div>
          </div>
        </div>
        {manifestInput}
      </div>
    );
  }

  public validate(values: ICloudFoundryServerGroupConfigurationSettingsProps) {
    const errors = {} as any;
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
            errors.manifest.routes = errors.manifest.routes || [];
            errors.manifest.routes.push(`A route was not specified`);
          }
          const regex = /^([a-zA-Z0-9_-]+)\.([a-zA-Z0-9_.-]+)(:[0-9]+)?([\/a-zA-Z0-9_-]+)?$/gm;
          if (route && regex.exec(route) === null) {
            errors.manifest = errors.manifest || {};
            errors.manifest.routes = errors.manifest.routes || [];
            errors.manifest.routes.push(
              `A route did not match the expected format "host.some.domain[:9999][/some/path]"`,
            );
          }
        });
      }
      if (values.manifest.services) {
        const existingServices: any = {};
        values.manifest.services.forEach(function(service) {
          if (!service) {
            errors.manifest = errors.manifest || {};
            errors.manifest.services = errors.manifest.services || [];
            errors.manifest.services.push(`A service was not specified`);
          }
          if (!!existingServices[service]) {
            errors.manifest = errors.manifest || {};
            errors.manifest.services = errors.manifest.services || [];
            errors.manifest.services.push(`Service "` + service + `" was duplicated`);
          }
          existingServices[service] = service;
        });
      }
      if (values.manifest.environment) {
        const existingKeys: any = {};
        values.manifest.environment.forEach(function(e) {
          if (!e.key || !e.value) {
            errors.manifest = errors.manifest || {};
            errors.manifest.environment = errors.manifest.environment || [];
            errors.manifest.environment.push(`An environment variable was not set`);
          } else {
            if (e.key) {
              const validKeyRegex = /^\w+$/g;
              if (!validKeyRegex.exec(e.key)) {
                errors.manifest = errors.manifest || {};
                errors.manifest.environment = errors.manifest.environment || [];
                errors.manifest.environment.push(
                  `'` + e.key + `' is an invalid environment variable name and must be alphanumeric`,
                );
              }
            }
            const value = existingKeys[e.key];
            if (!value) {
              existingKeys[e.key] = e.value;
            } else {
              errors.manifest = errors.manifest || {};
              errors.manifest.environment = errors.manifest.environment || [];
              errors.manifest.environment.push(
                `Duplicate environment variable: "` + e.key + `" set to "` + value + `" and "` + e.value + `"`,
              );
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

export const CloudFoundryServerGroupConfigurationSettings = wizardPage(ConfigurationSettingsImpl);
