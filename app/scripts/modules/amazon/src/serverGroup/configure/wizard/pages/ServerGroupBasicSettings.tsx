import * as React from 'react';
import * as DOMPurify from 'dompurify';
import { Field, FormikProps } from 'formik';

import {
  AccountSelectInput,
  DeploymentStrategySelector,
  HelpField,
  NameUtils,
  RegionSelectField,
  Application,
  ReactInjector,
  IServerGroup,
  IWizardPageComponent,
  TaskReason,
} from '@spinnaker/core';

import { IAmazonImage } from 'amazon/image';
import { SubnetSelectField } from 'amazon/subnet';

import { AmazonImageSelectInput } from '../../AmazonImageSelectInput';
import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';

const isExpressionLanguage = (field: string) => field && field.includes('${');
const isStackPattern = (stack: string) =>
  !isExpressionLanguage(stack) ? /^([a-zA-Z_0-9._${}]*(\${.+})*)*$/.test(stack) : true;
const isDetailPattern = (detail: string) =>
  !isExpressionLanguage(detail) ? /^([a-zA-Z_0-9._${}-]*(\${.+})*)*$/.test(detail) : true;

export interface IServerGroupBasicSettingsProps {
  app: Application;
  formik: FormikProps<IAmazonServerGroupCommand>;
}

export interface IServerGroupBasicSettingsState {
  selectedImage: IAmazonImage;
  namePreview: string;
  createsNewCluster: boolean;
  latestServerGroup: IServerGroup;
  showPreviewAsWarning: boolean;
}

export class ServerGroupBasicSettings
  extends React.Component<IServerGroupBasicSettingsProps, IServerGroupBasicSettingsState>
  implements IWizardPageComponent<IAmazonServerGroupCommand> {
  constructor(props: IServerGroupBasicSettingsProps) {
    super(props);
    const {
      amiName,
      region,
      viewState: { imageId },
    } = props.formik.values;
    const selectedImage = AmazonImageSelectInput.makeFakeImage(amiName, imageId, region);
    this.state = { ...this.getStateFromProps(props), selectedImage };
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

  private imageChanged = (image: IAmazonImage) => {
    const { setFieldValue, values } = this.props.formik;
    this.setState({ selectedImage: image });

    const virtualizationType = image && image.attributes.virtualizationType;
    const imageName = image && image.imageName;
    values.virtualizationType = virtualizationType;
    values.amiName = imageName;
    setFieldValue('virtualizationType', virtualizationType);
    setFieldValue('amiName', imageName);
    values.imageChanged(values);
  };

  private accountUpdated = (account: string): void => {
    const { setFieldValue, values } = this.props.formik;
    values.credentials = account;
    values.credentialsChanged(values);
    values.subnetChanged(values);
    setFieldValue('credentials', account);
  };

  private regionUpdated = (region: string): void => {
    const { values, setFieldValue } = this.props.formik;
    values.region = region;
    values.regionChanged(values);
    setFieldValue('region', region);
  };

  private subnetUpdated = (): void => {
    const { setFieldValue, values } = this.props.formik;
    values.subnetChanged(values);
    setFieldValue('subnetType', values.subnetType);
  };

  public validate(values: IAmazonServerGroupCommand): { [key: string]: string } {
    const errors: { [key: string]: string } = {};

    if (!isStackPattern(values.stack)) {
      errors.stack = 'Only dot(.) and underscore(_) special characters are allowed in the Stack field.';
    }

    if (!isDetailPattern(values.freeFormDetails)) {
      errors.freeFormDetails =
        'Only dot(.), underscore(_), and dash(-) special characters are allowed in the Detail field.';
    }

    if (!values.viewState.disableImageSelection && !values.amiName) {
      errors.amiName = 'Image required.';
    }

    return errors;
  }

  private clientRequestsChanged = () => {
    const { values, setFieldValue } = this.props.formik;
    values.toggleSuspendedProcess(values, 'AddToLoadBalancer');
    setFieldValue('suspendedProcesses', values.suspendedProcesses);
    this.setState({});
  };

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
    const { setFieldValue, values } = this.props.formik;
    values.stack = stack; // have to do it here to make sure it's done before calling values.clusterChanged
    setFieldValue('stack', stack);
    values.clusterChanged(values);
  };

  private freeFormDetailsChanged = (freeFormDetails: string) => {
    const { setFieldValue, values } = this.props.formik;
    values.freeFormDetails = freeFormDetails; // have to do it here to make sure it's done before calling values.clusterChanged
    setFieldValue('freeFormDetails', freeFormDetails);
    values.clusterChanged(values);
  };

  public componentWillReceiveProps(nextProps: IServerGroupBasicSettingsProps) {
    this.setState(this.getStateFromProps(nextProps));
  }

  private handleReasonChanged = (reason: string) => {
    this.props.formik.setFieldValue('reason', reason);
  };

  private strategyChanged = (values: IAmazonServerGroupCommand, strategy: any) => {
    values.onStrategyChange(values, strategy);
    this.props.formik.setFieldValue('strategy', strategy.key);
  };

  private onStrategyFieldChange = (key: string, value: any) => {
    this.props.formik.setFieldValue(key, value);
  };

  public render() {
    const { app, formik } = this.props;
    const { errors, values } = formik;
    const { createsNewCluster, latestServerGroup, namePreview, showPreviewAsWarning } = this.state;

    const accounts = values.backingData.accounts;
    const readOnlyFields = values.viewState.readOnlyFields || {};

    return (
      <div className="container-fluid form-horizontal">
        {values.regionIsDeprecated(values) && (
          <div className="form-group row">
            <div className="col-md-12 error-message">
              <div className="alert alert-danger">
                You are deploying into a deprecated region within the {values.credentials} account!
              </div>
            </div>
          </div>
        )}
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Account</div>
          <div className="col-md-7">
            <AccountSelectInput
              value={values.credentials}
              onChange={(evt: any) => this.accountUpdated(evt.target.value)}
              readOnly={readOnlyFields.credentials}
              accounts={accounts}
              provider="aws"
            />
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
        <SubnetSelectField
          readOnly={readOnlyFields.subnet}
          labelColumns={3}
          helpKey="aws.serverGroup.subnet"
          component={values}
          field="subnetType"
          region={values.region}
          application={app}
          subnets={values.backingData.filtered.subnetPurposes}
          onChange={this.subnetUpdated}
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
        {values.viewState.imageSourceText && (
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Image Source</div>
            <div className="col-md-7" style={{ marginTop: '5px' }}>
              <span dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(values.viewState.imageSourceText) }} />
            </div>
          </div>
        )}
        {!values.viewState.disableImageSelection && (
          <div className="form-group">
            <div className="col-md-3 sm-label-right">
              Image <HelpField id="aws.serverGroup.imageName" />
            </div>
            {isExpressionLanguage(values.amiName) ? (
              <Field name="amiName" />
            ) : (
              <AmazonImageSelectInput
                onChange={image => this.imageChanged(image)}
                value={this.state.selectedImage}
                application={app}
                credentials={values.credentials}
                region={values.region}
              />
            )}
          </div>
        )}
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Traffic <HelpField id="aws.serverGroup.traffic" />
          </div>
          <div className="col-md-9 checkbox">
            <label>
              <input
                type="checkbox"
                onChange={this.clientRequestsChanged}
                checked={!values.processIsSuspended(values, 'AddToLoadBalancer')}
                disabled={values.strategy !== '' && values.strategy !== 'custom'}
              />
              Send client requests to new instances
            </label>
          </div>
        </div>
        {!values.viewState.disableStrategySelection && values.selectedProvider && (
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
                  <p>Your server group will be in the cluster:</p>
                  <p>
                    <strong>
                      {namePreview}
                      {createsNewCluster && <span> (new cluster)</span>}
                    </strong>
                  </p>
                  {!createsNewCluster && values.viewState.mode === 'create' && latestServerGroup && (
                    <div className="text-left">
                      <p>There is already a server group in this cluster. Do you want to clone it?</p>
                      <p>
                        Cloning copies the entire configuration from the selected server group, allowing you to modify
                        whichever fields (e.g. image) you need to change in the new server group.
                      </p>
                      <p>
                        To clone a server group, select "Clone" from the "Server Group Actions" menu in the details view
                        of the server group.
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
        <TaskReason reason={values.reason} onChange={this.handleReasonChanged} />
      </div>
    );
  }
}
