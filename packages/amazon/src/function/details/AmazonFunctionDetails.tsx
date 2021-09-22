import { isEmpty } from 'lodash';
import React from 'react';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  AccountTag,
  Application,
  CollapsibleSection,
  Details,
  IFunction,
  IOverridableProps,
  Overrides,
} from '@spinnaker/core';

import { FunctionActions } from './FunctionActions';
import { IAmazonFunction, IAmazonFunctionSourceData } from '../../domain';
import { AwsReactInjector } from '../../reactShims';

export interface IFunctionFromStateParams {
  account: string;
  region: string;
  functionName: string;
}

export interface IAmazonFunctionDetailsProps extends IOverridableProps {
  app: Application;
  functionObj: IFunction;
}

@Overrides('function.details', 'aws')
export class AmazonFunctionDetails extends React.Component<IAmazonFunctionDetailsProps, any> {
  private destroy$ = new Subject();
  constructor(props: IAmazonFunctionDetailsProps) {
    super(props);
    this.state = {
      loading: true,
    };
  }

  public extractFunction(): void {
    const { app, functionObj: functionFromProps } = this.props;
    const functionDef: IFunction = app.functions.data.find((test: IFunction) => {
      return (
        test.functionName === functionFromProps.functionName &&
        test.region === functionFromProps.region &&
        test.account === functionFromProps.account
      );
    });

    if (functionDef) {
      observableFrom(
        AwsReactInjector.functionReader.getFunctionDetails(
          'aws',
          functionFromProps.account,
          functionFromProps.region,
          functionFromProps.functionName,
        ),
      )
        .pipe(takeUntil(this.destroy$))
        .subscribe((details: IAmazonFunctionSourceData[]) => {
          if (details.length) {
            this.setState({
              functionDef: details[0] as IAmazonFunction,
              loading: false,
            });
          }
        });
    } else {
      this.setState({
        functionDef: {},
        loading: false,
      });
    }
  }

  public componentDidMount(): void {
    const { app } = this.props;
    const dataSource = app.functions;
    observableFrom(dataSource.ready())
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        const dataSourceUnsubscribe = dataSource.onRefresh(null, () => this.extractFunction());
        this.setState({ dataSourceUnsubscribe });
        this.extractFunction();
      });
  }

  public componentWillUnmount() {
    this.state.dataSourceUnsubscribe && this.state.dataSourceUnsubscribe();
    this.destroy$.next();
  }

  public render() {
    const { app } = this.props;
    const { loading, functionDef } = this.state;
    if (loading) {
      // Don't bother computing any children if we're loading
      return <Details loading={loading} />;
    }

    const functionDetails = (
      <dl className="horizontal-when-filters-collapsed dl-horizontal dl-narrow">
        <dt>Last Modified </dt>
        <dd>{functionDef.lastModified}</dd>
        <dt>In</dt>
        <dd>
          <AccountTag account={functionDef.account} /> {functionDef.region}
        </dd>
        <dt>VPC</dt>
        <dd>{functionDef.vpcConfig ? functionDef.vpcConfig.vpcId : 'Default'}</dd>
        <dt>Function ARN</dt>
        <dd>{functionDef.functionArn}</dd>
        <dt>Revision ID</dt>
        <dd>{functionDef.revisionId}</dd>
        <dt>Version</dt>
        <dd>{functionDef.version}</dd>
        <dt>Event Source</dt>
        <dd>
          {functionDef.eventSourceMappings && functionDef.eventSourceMappings.length !== 0
            ? functionDef.eventSourceMappings
            : 'None'}
        </dd>
      </dl>
    );

    const functionDetailsSection = (
      <CollapsibleSection heading="Function Details">{functionDetails}</CollapsibleSection>
    );

    return (
      <Details loading={this.state.loading}>
        {isEmpty(this.state.functionDef) ? (
          'Function not found.'
        ) : (
          <Details.Header
            icon={<i className="fa fa-xs fa-fw fa-asterisk" />}
            name={this.state.functionDef.functionName}
          >
            <div className="actions">
              <FunctionActions
                app={app}
                functionDef={functionDef}
                functionFromParams={{
                  account: this.state.functionDef.account,
                  region: this.state.functionDef.region,
                  functionName: this.state.functionDef.functionName,
                }}
              />
            </div>
          </Details.Header>
        )}
        {!isEmpty(this.state.functionDef) ? functionDetailsSection : ''}
      </Details>
    );
  }
}
