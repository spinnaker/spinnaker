import { Field, FormikErrors, FormikProps } from 'formik';
import React from 'react';

import {
  AccountSelectInput,
  Application,
  DeployingIntoManagedClusterWarning,
  DeploymentStrategySelector,
  HelpField,
  IServerGroup,
  IWizardPageComponent,
  Markdown,
  NameUtils,
  ReactInjector,
  RegionSelectField,
  ServerGroupDetailsField,
  ServerGroupNamePreview,
  SETTINGS,
  TaskReason,
} from '@spinnaker/core';

import { AmazonImageSelectInput } from '../../AmazonImageSelectInput';
import { AWSProviderSettings } from '../../../../aws.settings';
import { IAmazonImage } from '../../../../image';
import { IAmazonServerGroupCommand } from '../../serverGroupConfiguration.service';
import { SubnetSelectField } from '../../../../subnet';

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

    const namePreview = NameUtils.getClusterName(app.name, values.stack, values.freeFormDetails);
    const createsNewCluster = !app.clusters.find((c) => c.name === namePreview);

    const inCluster = (app.serverGroups.data as IServerGroup[])
      .filter((serverGroup) => {
        return (
          serverGroup.cluster === namePreview &&
          serverGroup.account === values.credentials &&
          serverGroup.region === values.region
        );
      })
      .sort((a, b) => a.createdTime - b.createdTime);
    const latestServerGroup = inCluster.length ? inCluster.pop() : null;

    return { namePreview, createsNewCluster, latestServerGroup };
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

    if (image && SETTINGS.disabledImages?.length && AWSProviderSettings.serverGroups?.enableIPv6) {
      const isImageDisabled = SETTINGS.disabledImages.some((i) => image.imageName.includes(i));
      if (isImageDisabled) {
        setFieldValue('associateIPv6Address', false);
      }
    }
  };

  private accountUpdated = (account: string): void => {
    const { setFieldValue, values } = this.props.formik;
    values.credentials = account;
    values.credentialsChanged(values);
    values.subnetChanged(values);
    setFieldValue('credentials', account);

    const accountDetails = values.backingData.credentialsKeyedByAccount[account];
    const enableIPv6InTest =
      AWSProviderSettings?.serverGroups?.enableIPv6 &&
      AWSProviderSettings?.serverGroups?.setIPv6InTest &&
      accountDetails.environment === 'test';

    setFieldValue('associateIPv6Address', enableIPv6InTest);
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

  public validate(values: IAmazonServerGroupCommand): FormikErrors<IAmazonServerGroupCommand> {
    const errors: FormikErrors<IAmazonServerGroupCommand> = {};

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

    // this error is added exclusively to disable the "create/clone" button - it is not visible aside from the warning
    // rendered by the DeployingIntoManagedClusterWarning component
    if (values.resourceSummary) {
      errors.resourceSummary = { id: 'Cluster is managed' };
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
    const { createsNewCluster, latestServerGroup, namePreview } = this.state;

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
        <DeployingIntoManagedClusterWarning app={app} formik={formik} />
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
          showSubnetWarning={true}
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
              onChange={(e) => this.stackChanged(e.target.value)}
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
        <ServerGroupDetailsField app={app} formik={formik} />
        {values.viewState.imageSourceText && (
          <div className="form-group">
            <div className="col-md-3 sm-label-right">Image Source</div>
            <div className="col-md-7" style={{ marginTop: '5px' }}>
              <Markdown tag="span" message={values.viewState.imageSourceText} />
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
                onChange={(image) => this.imageChanged(image)}
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
          <ServerGroupNamePreview
            createsNewCluster={createsNewCluster}
            latestServerGroupName={latestServerGroup?.name}
            mode={values.viewState.mode}
            namePreview={namePreview}
            navigateToLatestServerGroup={this.navigateToLatestServerGroup}
          />
        )}
        <TaskReason reason={values.reason} onChange={this.handleReasonChanged} />
      </div>
    );
  }
}
