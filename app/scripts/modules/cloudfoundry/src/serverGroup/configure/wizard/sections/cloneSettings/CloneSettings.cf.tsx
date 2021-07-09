import { FormikErrors, FormikProps } from 'formik';
import React from 'react';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import {
  AccountService,
  Application,
  FormikFormField,
  IAccount,
  IDeploymentStrategy,
  IWizardPageComponent,
  Markdown,
  ReactSelectInput,
  StageConstants,
} from '@spinnaker/core';
import { FormikAccountRegionClusterSelector } from '../../../../../presentation';

import { ICloudFoundryCreateServerGroupCommand } from '../../../serverGroupConfigurationModel.cf';

import '../../../../../common/cloudFoundry.less';

export interface ICloudFoundryCloneSettingsProps {
  formik: FormikProps<ICloudFoundryCreateServerGroupCommand>;
  application: Application;
}

export interface ICloudFoundryCloneSettingsState {
  accounts: IAccount[];
}

export class CloudFoundryServerGroupCloneSettings
  extends React.Component<ICloudFoundryCloneSettingsProps, ICloudFoundryCloneSettingsState>
  implements IWizardPageComponent<ICloudFoundryCreateServerGroupCommand> {
  private destroy$ = new Subject();
  public state: ICloudFoundryCloneSettingsState = {
    accounts: [],
  };

  public componentDidMount(): void {
    observableFrom(AccountService.listAccounts('cloudfoundry'))
      .pipe(takeUntil(this.destroy$))
      .subscribe((accounts) => this.setState({ accounts }));
  }

  public componentWillUnmount(): void {
    this.destroy$.next();
  }

  private strategyOptionRenderer = (option: IDeploymentStrategy) => {
    return (
      <div className="body-regular">
        <strong>
          <Markdown tag="span" message={option.label} />
        </strong>
        <div>
          <Markdown tag="span" message={option.description} />
        </div>
      </div>
    );
  };

  public render(): JSX.Element {
    const { application, formik } = this.props;
    const { accounts } = this.state;
    return (
      <div className="form-group">
        <FormikAccountRegionClusterSelector
          componentName={'source'}
          accounts={accounts}
          application={application}
          cloudProvider={'cloudfoundry'}
          clusterField={'clusterName'}
          credentialsField={'account'}
          formik={formik}
        />
        <div className="col-md-9">
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name={'target'}
              label="Target"
              input={(props) => (
                <ReactSelectInput
                  inputClassName="cloudfoundry-react-select"
                  {...props}
                  options={StageConstants.TARGET_LIST.map((t) => {
                    return {
                      label: t.label,
                      value: t.val,
                      description: t.description,
                    };
                  })}
                  optionRenderer={this.strategyOptionRenderer}
                  clearable={false}
                />
              )}
              required={true}
            />
          </div>
        </div>
      </div>
    );
  }

  public validate(_values: ICloudFoundryCreateServerGroupCommand) {
    const errors = {} as FormikErrors<ICloudFoundryCreateServerGroupCommand>;

    return errors;
  }
}
