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
  IStage,
  ReactSelectInput,
} from '@spinnaker/core';

export interface IFormikAccountRegionSelectorProps {
  accounts: IAccount[];
  application: Application;
  cloudProvider: string;
  componentName?: string;
  credentialsField?: string;
  formik: FormikProps<IStage>;
}

export interface IFormikAccountRegionSelectorState {
  availableRegions: string[];
  cloudProvider: string;
  componentName: string;
  credentialsField: string;
}

export class FormikAccountRegionSelector extends React.Component<
  IFormikAccountRegionSelectorProps,
  IFormikAccountRegionSelectorState
> {
  private destroy$ = new Subject();

  constructor(props: IFormikAccountRegionSelectorProps) {
    super(props);
    const credentialsField = props.credentialsField || 'credentials';
    this.state = {
      availableRegions: [],
      cloudProvider: props.cloudProvider,
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
    this.setRegionList(credentials);
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

  public accountChanged = (credentials: string): void => {
    this.setRegionList(credentials);
  };

  public render() {
    const { accounts } = this.props;
    const { credentialsField, availableRegions, componentName } = this.state;
    return (
      <div className="col-md-9">
        <div className="sp-margin-m-bottom">
          <FormikFormField
            name={componentName ? `${componentName}.${credentialsField}` : `${credentialsField}`}
            label="Account"
            input={(props) => (
              <ReactSelectInput
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
            input={(props) => <ReactSelectInput {...props} stringOptions={availableRegions} clearable={false} />}
            required={true}
          />
        </div>
      </div>
    );
  }
}
