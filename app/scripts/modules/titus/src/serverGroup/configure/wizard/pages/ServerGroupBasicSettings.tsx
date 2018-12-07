import * as React from 'react';
import { Field, FormikErrors } from 'formik';

import {
  DeploymentStrategySelector,
  HelpField,
  IWizardPageProps,
  wizardPage,
  NameUtils,
  RegionSelectField,
  Application,
  ReactInjector,
  IServerGroup,
  AccountSelectInput,
  AccountTag,
} from '@spinnaker/core';

import { DockerImageAndTagSelector, DockerImageUtils } from '@spinnaker/docker';

import { ITitusServerGroupCommand } from '../../../configure/serverGroupConfiguration.service';

const isNotExpressionLanguage = (field: string) => field && !field.includes('${');
const isStackPattern = (stack: string) =>
  isNotExpressionLanguage(stack) ? /^([a-zA-Z_0-9._${}]*(\${.+})*)*$/.test(stack) : true;
const isDetailPattern = (detail: string) =>
  isNotExpressionLanguage(detail) ? /^([a-zA-Z_0-9._$-{}\\\^~]*(\${.+})*)*$/.test(detail) : true;

export interface IServerGroupBasicSettingsProps extends IWizardPageProps<ITitusServerGroupCommand> {
  app: Application;
}

export interface IServerGroupBasicSettingsState {
  namePreview: string;
  createsNewCluster: boolean;
  latestServerGroup: IServerGroup;
  showPreviewAsWarning: boolean;
}

class ServerGroupBasicSettingsImpl extends React.Component<
  IServerGroupBasicSettingsProps,
  IServerGroupBasicSettingsState
> {
  public static LABEL = 'Basic Settings';

  constructor(props: IServerGroupBasicSettingsProps) {
    super(props);

    const { values, setFieldValue } = this.props.formik;
    if (values.imageId && !values.imageId.includes('${')) {
      const { digest, organization, repository, tag } = DockerImageUtils.splitImageId(values.imageId);
      setFieldValue('digest', digest);
      setFieldValue('organization', organization);
      setFieldValue('repository', repository);
      setFieldValue('tag', tag);
    }

    this.state = {
      ...this.getStateFromProps(props),
    };
  }

  private getStateFromProps(props: IServerGroupBasicSettingsProps) {
    const { app } = props;
    const { values } = props.formik;
    const { mode } = values.viewState;
    const namePreview = NameUtils.getClusterName(app.name, values.stack, values.freeFormDetails);
    const createsNewCluster = !app.clusters.find(c => c.name === namePreview);
    const showPreviewAsWarning = (mode === 'create' && !createsNewCluster) || (mode !== 'create' && createsNewCluster);

    const inCluster = (app.serverGroups.data as IServerGroup[])
      .filter(serverGroup => {
        return (
          serverGroup.cluster === namePreview &&
          serverGroup.account === values.credentials &&
          serverGroup.region === values.region
        );
      })
      .sort((a, b) => a.createdTime - b.createdTime);
    const latestServerGroup = inCluster.length ? inCluster.pop() : null;

    return { namePreview, createsNewCluster, latestServerGroup, showPreviewAsWarning };
  }

  private accountUpdated = (account: string): void => {
    const { setFieldValue, values } = this.props.formik;
    values.credentials = account;
    values.credentialsChanged(values);
    setFieldValue('account', account);
    setFieldValue('credentials', account);
  };

  private regionUpdated = (region: string): void => {
    const { values, setFieldValue } = this.props.formik;
    values.region = region;
    values.regionChanged(values);
    setFieldValue('region', region);
  };

  public validate(values: ITitusServerGroupCommand) {
    const errors: FormikErrors<ITitusServerGroupCommand> = {};

    if (!isStackPattern(values.stack)) {
      errors.stack = 'Only dot(.) and underscore(_) special characters are allowed in the Stack field.';
    }

    if (!isDetailPattern(values.freeFormDetails)) {
      errors.freeFormDetails =
        'Only dot(.), underscore(_), caret (^), tilde (~), and dash(-) special characters are allowed in the Detail field.';
    }

    if (!values.viewState.disableImageSelection) {
      if (!values.repository) {
        errors.repository = 'Image is required.';
      }
      if (!values.tag) {
        errors.tag = 'Tag is required.';
      }
    }

    return errors;
  }

  private navigateToLatestServerGroup = () => {
    const { values } = this.props.formik;
    const { latestServerGroup } = this.state;

    const params = {
      provider: values.selectedProvider,
      accountId: latestServerGroup.account,
      region: latestServerGroup.region,
      serverGroup: latestServerGroup.name,
    };

    const { $state } = ReactInjector;
    if ($state.is('home.applications.application.insight.clusters')) {
      $state.go('.serverGroup', params);
    } else {
      $state.go('^.serverGroup', params);
    }
  };

  private stackChanged = (stack: string) => {
    this.props.formik.setFieldValue('stack', stack);
  };

  private freeFormDetailsChanged = (freeFormDetails: string) => {
    this.props.formik.setFieldValue('freeFormDetails', freeFormDetails);
  };

  public componentWillReceiveProps(nextProps: IServerGroupBasicSettingsProps) {
    this.setState(this.getStateFromProps(nextProps));
  }

  private strategyChanged = (values: ITitusServerGroupCommand, strategy: any) => {
    values.onStrategyChange(values, strategy);
    this.props.formik.setFieldValue('strategy', strategy.key);
  };

  private dockerValuesChanged = (dockerValues: any) => {
    Object.keys(dockerValues).forEach(key => {
      this.props.formik.setFieldValue(key, dockerValues[key]);
    });
  };

  private onStrategyFieldChange = (key: string, value: any) => {
    this.props.formik.setFieldValue(key, value);
  };

  public render() {
    const { errors, setFieldValue, values } = this.props.formik;
    const { createsNewCluster, latestServerGroup, namePreview, showPreviewAsWarning } = this.state;

    const accounts = values.backingData.accounts;
    const readOnlyFields = values.viewState.readOnlyFields || {};

    return (
      <div className="container-fluid form-horizontal">
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Account</div>
          <div className="col-md-7">
            <AccountSelectInput
              readOnly={readOnlyFields.credentials}
              component={values}
              field="credentials"
              accounts={accounts}
              provider="titus"
              onChange={this.accountUpdated}
            />
            {values.credentials !== undefined && (
              <div className="small">
                Uses resources from the Amazon account{' '}
                <AccountTag account={values.backingData.credentialsKeyedByAccount[values.credentials].awsAccount} />
              </div>
            )}
          </div>
        </div>
        <RegionSelectField
          readOnly={readOnlyFields.region}
          labelColumns={3}
          component={values}
          field="region"
          account={values.credentials}
          regions={values.backingData.filtered.regions}
          onChange={this.regionUpdated}
        />
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Stack <HelpField id="aws.serverGroup.stack" />
          </div>
          <div className="col-md-7">
            <input
              type="text"
              className="form-control input-sm no-spel"
              value={values.stack}
              onChange={e => this.stackChanged(e.target.value)}
            />
          </div>
        </div>
        {errors.stack && (
          <div className="form-group row slide-in">
            <div className="col-sm-9 col-sm-offset-2 error-message">
              <span>{errors.stack}</span>
            </div>
          </div>
        )}
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Detail <HelpField id="aws.serverGroup.detail" />
          </div>
          <div className="col-md-7">
            <input
              type="text"
              className="form-control input-sm no-spel"
              value={values.freeFormDetails}
              onChange={e => this.freeFormDetailsChanged(e.target.value)}
            />
          </div>
        </div>
        {errors.freeFormDetails && (
          <div className="form-group row slide-in">
            <div className="col-sm-9 col-sm-offset-2 error-message">
              <span>{errors.freeFormDetails}</span>
            </div>
          </div>
        )}

        {!values.viewState.disableImageSelection && (
          <DockerImageAndTagSelector
            specifyTagByRegex={false}
            account={values.credentials}
            digest={values.digest}
            imageId={values.imageId}
            organization={values.organization}
            registry={values.registry}
            repository={values.repository}
            tag={values.tag}
            showRegistry={false}
            deferInitialization={values.deferredInitialization}
            labelClass="col-md-3"
            fieldClass="col-md-7"
            onChange={this.dockerValuesChanged}
          />
        )}
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>Entrypoint</b>
          </div>
          <div className="col-md-7">
            <Field type="text" className="form-control input-sm no-spel" name="entryPoint" />
          </div>
        </div>

        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Traffic <HelpField id="titus.serverGroup.traffic" />
          </div>
          <div className="col-md-7">
            <div className="checkbox">
              <label>
                <input
                  type="checkbox"
                  checked={values.inService}
                  onChange={e => setFieldValue('inService', e.target.checked)}
                  disabled={values.strategy !== '' && values.strategy !== 'custom'}
                />{' '}
                Send client requests to new instances
              </label>
            </div>
          </div>
        </div>
        {!values.viewState.disableStrategySelection &&
          values.selectedProvider && (
            <DeploymentStrategySelector
              command={values}
              onFieldChange={this.onStrategyFieldChange}
              onStrategyChange={this.strategyChanged}
            />
          )}
        {!values.viewState.hideClusterNamePreview && (
          <div className="form-group">
            <div className="col-md-12">
              <div className={`well-compact ${showPreviewAsWarning ? 'alert alert-warning' : 'well'}`}>
                <h5 className="text-center">
                  <p>Your Titus Job name will be:</p>
                  <p>
                    <strong>
                      {namePreview}
                      {createsNewCluster && <span> (new cluster)</span>}
                    </strong>
                  </p>
                  {!createsNewCluster &&
                    values.viewState.mode === 'create' &&
                    latestServerGroup && (
                      <div className="text-left">
                        <p>There is already a server group in this cluster. Do you want to clone it?</p>
                        <p>
                          Cloning copies the entire configuration from the selected server group, allowing you to modify
                          whichever fields (e.g. image) you need to change in the new server group.
                        </p>
                        <p>
                          To clone a server group, select "Clone" from the "Server Group Actions" menu in the details
                          view of the server group.
                        </p>
                        <p>
                          <a className="clickable" onClick={this.navigateToLatestServerGroup}>
                            Go to details for {latestServerGroup.name}
                          </a>
                        </p>
                      </div>
                    )}
                </h5>
              </div>
            </div>
          </div>
        )}
      </div>
    );
  }
}

export const ServerGroupBasicSettings = wizardPage(ServerGroupBasicSettingsImpl);
