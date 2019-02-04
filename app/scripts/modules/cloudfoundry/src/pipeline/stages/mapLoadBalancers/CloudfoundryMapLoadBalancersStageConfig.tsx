import * as React from 'react';

import {
  Application,
  IAccount,
  IPipeline,
  IStageConfigProps,
  NgReact,
  StageConfigField,
  StageConstants,
} from '@spinnaker/core';

import { AccountRegionClusterSelector, Routes } from 'cloudfoundry/presentation';
import { Formik } from 'formik';

interface ICloudfoundryLoadBalancerStageConfigProps extends IStageConfigProps {
  accounts: IAccount[];
  pipeline: IPipeline;
}

interface ICloudFoundryMapLoadBalancersValues {
  routes: string[];
}

interface ICloudfoundryMapLoadBalancersStageConfigState {
  application: Application;
  cloudProvider: string;
  credentials: string;
  initialValues: ICloudFoundryMapLoadBalancersValues;
  pipeline: IPipeline;
  region: string;
  target: string;
}

export class CloudfoundryMapLoadBalancersStageConfig extends React.Component<
  ICloudfoundryLoadBalancerStageConfigProps,
  ICloudfoundryMapLoadBalancersStageConfigState
> {
  private formikRef = React.createRef<Formik<ICloudFoundryMapLoadBalancersValues>>();

  constructor(props: ICloudfoundryLoadBalancerStageConfigProps) {
    super(props);
    props.stage.cloudProvider = 'cloudfoundry';
    this.state = {
      application: props.application,
      cloudProvider: 'cloudfoundry',
      credentials: props.stage.credentials,
      initialValues: {
        routes: props.stage.loadBalancerNames,
      },
      pipeline: props.pipeline,
      region: props.stage.region,
      target: props.stage.target,
    };
  }

  private targetUpdated = (target: string) => {
    this.setState({ target });
    this.props.stage.target = target;
    this.props.stageFieldUpdated();
  };

  private componentUpdated = (stage: any): void => {
    this.props.stage.credentials = stage.credentials;
    this.props.stage.region = stage.region;
    this.props.stage.cluster = stage.cluster;
    this.props.stage.loadBalancerNames = stage.loadBalancerNames;
    this.props.stageFieldUpdated();
  };

  public render() {
    const { accounts, stage } = this.props;
    const { application, initialValues, pipeline, target } = this.state;
    const { TargetSelect } = NgReact;
    return (
      <div className="form-horizontal">
        {!pipeline.strategy && (
          <AccountRegionClusterSelector
            accounts={accounts}
            application={application}
            cloudProvider={'cloudfoundry'}
            isSingleRegion={true}
            onComponentUpdate={this.componentUpdated}
            component={stage}
          />
        )}
        <StageConfigField label="Target">
          <TargetSelect model={{ target }} options={StageConstants.TARGET_LIST} onChange={this.targetUpdated} />
        </StageConfigField>
        <Formik<ICloudFoundryMapLoadBalancersValues>
          ref={this.formikRef}
          initialValues={initialValues}
          onSubmit={null}
          render={() => {
            return (
              <Routes
                fieldName={'routes'}
                onChange={(routes: string[]) => {
                  stage.loadBalancerNames = routes;
                  this.componentUpdated(stage);
                }}
              />
            );
          }}
        />
      </div>
    );
  }
}
