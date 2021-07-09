import { FormikProps } from 'formik';
import { get } from 'lodash';
import React from 'react';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  Application,
  AppListExtractor,
  FormikFormField,
  IAccount,
  IServerGroup,
  IServerGroupFilter,
  ReactSelectInput,
} from '@spinnaker/core';
import { ICloudFoundryCreateServerGroupCommand } from '../../../serverGroup';

export interface IFormikAccountRegionClusterSelectorProps {
  accounts: IAccount[];
  application: Application;
  cloudProvider: string;
  clusterField?: string;
  componentName?: string;
  credentialsField?: string;
  formik: FormikProps<ICloudFoundryCreateServerGroupCommand>;
}

export interface IFormikAccountRegionClusterSelectorState {
  availableRegions: string[];
  cloudProvider: string;
  clusterField: string;
  clusters: string[];
  componentName: string;
  credentialsField: string;
}

export class FormikAccountRegionClusterSelector extends React.Component<
  IFormikAccountRegionClusterSelectorProps,
  IFormikAccountRegionClusterSelectorState
> {
  private destroy$ = new Subject();

  constructor(props: IFormikAccountRegionClusterSelectorProps) {
    super(props);
    const credentialsField = props.credentialsField || 'credentials';
    const clusterField = props.clusterField || 'cluster';
    this.state = {
      availableRegions: [],
      cloudProvider: props.cloudProvider,
      clusterField,
      clusters: [],
      componentName: props.componentName || '',
      credentialsField,
    };
  }

  public componentDidMount(): void {
    const { componentName, formik } = this.props;
    const { credentialsField } = this.state;
    const credentials = get(
      formik.values,
      componentName ? `${componentName}.${credentialsField}` : `${credentialsField}`,
      undefined,
    );
    const region = get(formik.values, componentName ? `${componentName}.region` : 'region', undefined);
    this.setRegionList(credentials);
    this.setClusterList(credentials, [region]);
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private setRegionList = (credentials: string): void => {
    const { application } = this.props;
    const accountFilter: IServerGroupFilter = (serverGroup: IServerGroup) =>
      serverGroup ? serverGroup.account === credentials : true;
    observableFrom(application.ready())
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        const availableRegions = AppListExtractor.getRegions([application], accountFilter);
        availableRegions.sort();
        this.setState({ availableRegions });
      });
  };

  private setClusterList = (credentials: string, regions: string[]): void => {
    const { application } = this.props;
    observableFrom(application.ready())
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        const clusterFilter = AppListExtractor.clusterFilterForCredentialsAndRegion(credentials, regions);
        const clusters = AppListExtractor.getClusters([application], clusterFilter);
        this.setState({ clusters });
      });
  };

  public accountChanged = (credentials: string): void => {
    this.setRegionList(credentials);
    this.setClusterList(credentials, []);
  };

  public regionChanged = (region: string): void => {
    const { componentName, formik } = this.props;
    const { credentialsField } = this.state;
    const credentials = get(
      formik.values,
      componentName ? `${componentName}.${credentialsField}` : `${credentialsField}`,
      undefined,
    );
    this.setClusterList(credentials, [region]);
  };

  public render() {
    const { accounts } = this.props;
    const { credentialsField, availableRegions, clusters, clusterField, componentName } = this.state;
    return (
      <div className="col-md-9">
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name={componentName ? `${componentName}.${credentialsField}` : `${credentialsField}`}
            label="Account"
            input={(props) => (
              <ReactSelectInput
                inputClassName="cloudfoundry-react-select"
                {...props}
                stringOptions={accounts && accounts.map((acc: IAccount) => acc.name)}
                clearable={false}
              />
            )}
            onChange={this.accountChanged}
            required={true}
          />
        </div>

        <div className="sp-margin-m-bottom">
          <FormikFormField
            name={componentName ? `${componentName}.region` : 'region'}
            label="Region"
            input={(props) => (
              <ReactSelectInput
                inputClassName="cloudfoundry-react-select"
                {...props}
                stringOptions={availableRegions}
                clearable={false}
              />
            )}
            onChange={this.regionChanged}
            required={true}
          />
        </div>

        <div className="sp-margin-m-bottom">
          <FormikFormField
            name={componentName ? `${componentName}.${clusterField}` : `${clusterField}`}
            label="Cluster"
            input={(props) => (
              <ReactSelectInput
                inputClassName="cloudfoundry-react-select"
                {...props}
                stringOptions={clusters}
                clearable={false}
              />
            )}
            required={true}
          />
        </div>
      </div>
    );
  }
}
