import { Field, FormikErrors, FormikProps } from 'formik';
import React from 'react';

import { SubnetSelectField } from '@spinnaker/amazon';
import {
  AccountSelectInput,
  AccountTag,
  Application,
  DeployingIntoManagedClusterWarning,
  DeploymentStrategySelector,
  HelpField,
  IServerGroup,
  IWizardPageComponent,
  NameUtils,
  ReactInjector,
  RegionSelectField,
  ServerGroupDetailsField,
  ServerGroupNamePreview,
} from '@spinnaker/core';
import { DockerImageAndTagSelector, DockerImageUtils } from '@spinnaker/docker';

import { ITitusServerGroupCommand } from '../../../configure/serverGroupConfiguration.service';

const isNotExpressionLanguage = (field: string) => field && !field.includes('${');

// Allow dot, underscore, and spel
const isStackPattern = (stack: string) => (isNotExpressionLanguage(stack) ? /^([\w.]+|\${[^}]+})*$/.test(stack) : true);

// Allow dot, underscore, caret, tilde, dash and spel
const isDetailPattern = (detail: string) =>
  isNotExpressionLanguage(detail) ? /^([\w.^~-]+|\${[^}]+})*$/.test(detail) : true;

export interface IServerGroupBasicSettingsProps {
  app: Application;
  formik: FormikProps<ITitusServerGroupCommand>;
}

export interface IServerGroupBasicSettingsState {
  namePreview: string;
  createsNewCluster: boolean;
  latestServerGroup: IServerGroup;
}

export class ServerGroupBasicSettings
  extends React.Component<IServerGroupBasicSettingsProps, IServerGroupBasicSettingsState>
  implements IWizardPageComponent<ITitusServerGroupCommand> {
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

  private accountUpdated = (account: string): void => {
    const { setFieldValue, values } = this.props.formik;
    values.credentials = account;
    values.credentialsChanged(values);
    setFieldValue('account', account);
    setFieldValue('credentials', account);

    const accountDetails = values.backingData.credentialsKeyedByAccount[account];

    const newAttr = {
      ...values.containerAttributes,
      'titusParameter.agent.assignIPv6Address': accountDetails.environment === 'test' ? 'true' : 'false',
    };
    setFieldValue('containerAttributes', newAttr);
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
      if (!values.imageId) {
        errors.imageId = 'Image is required.';
      }
    }

    // this error is added exclusively to disable the "create/clone" button - it is not visible aside from the warning
    // rendered by the DeployingIntoManagedClusterWarning component
    if (values.resourceSummary) {
      errors.resourceSummary = { id: 'Cluster is managed' };
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
    const { formik } = this.props;
    formik.setFieldValue('stack', stack);
    formik.values.clusterChanged(formik.values);
  };

  public componentWillReceiveProps(nextProps: IServerGroupBasicSettingsProps) {
    this.setState(this.getStateFromProps(nextProps));
  }

  private strategyChanged = (values: ITitusServerGroupCommand, strategy: any) => {
    values.onStrategyChange(values, strategy);
    this.props.formik.setFieldValue('strategy', strategy.key);
  };

  private dockerValuesChanged = (dockerValues: any) => {
    Object.keys(dockerValues).forEach((key) => {
      this.props.formik.setFieldValue(key, dockerValues[key]);
    });
  };

  private onStrategyFieldChange = (key: string, value: any) => {
    this.props.formik.setFieldValue(key, value);
  };

  private onSubnetChange = () => {
    const { setFieldValue, values } = this.props.formik;
    values.subnetChanged(values);
    setFieldValue('subnetType', values.subnetType);
  };

  public render() {
    const { app, formik } = this.props;
    const { errors, setFieldValue, values } = formik;
    const { createsNewCluster, latestServerGroup, namePreview } = this.state;

    const accounts = values.backingData.accounts;
    const readOnlyFields = values.viewState.readOnlyFields || {};

    const customImage = values.imageId && values.imageId !== '${trigger.properties.imageName}';

    return (
      <div className="container-fluid form-horizontal">
        <DeployingIntoManagedClusterWarning app={app} formik={formik} />
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Account</div>
          <div className="col-md-7">
            <AccountSelectInput
              value={values.credentials}
              onChange={(evt: any) => this.accountUpdated(evt.target.value)}
              readOnly={readOnlyFields.credentials}
              accounts={accounts}
              provider="titus"
            />
            {values.credentials !== undefined && (
              <div className="small">
                Uses resources from the Amazon account{' '}
                <AccountTag
                  account={
                    values.backingData.credentialsKeyedByAccount[values.credentials] &&
                    values.backingData.credentialsKeyedByAccount[values.credentials].awsAccount
                  }
                />
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
        <SubnetSelectField
          application={app}
          component={values}
          field="subnetType"
          helpKey="titus.serverGroup.subnet"
          labelColumns={3}
          onChange={this.onSubnetChange}
          region={values.region}
          subnets={values.backingData.filtered.subnetPurposes}
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
              value={values.stack || ''}
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
            onChange={this.dockerValuesChanged}
          />
        )}
        {values.viewState.disableImageSelection && customImage && (
          <div className="form-group">
            <div className="col-md-3 sm-label-right">
              <b>Image</b> <HelpField id="titus.deploy.imageId" />
            </div>
            <div className="col-md-7 sp-padding-xs-yaxis">{values.imageId}</div>
          </div>
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
                  onChange={(e) => setFieldValue('inService', e.target.checked)}
                  disabled={values.strategy !== '' && values.strategy !== 'custom'}
                />{' '}
                Send client requests to new instances
              </label>
            </div>
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
      </div>
    );
  }
}
