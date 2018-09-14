import * as React from 'react';

import { FormikProps } from 'formik';

import { wizardPage, IWizardPageProps, IJob } from '@spinnaker/core';

export interface IDisclaimerProps {}

class CfDisclaimerWizardPage extends React.Component<IDisclaimerProps & IWizardPageProps & FormikProps<IJob>> {
  public static LABEL = 'Disclaimer';

  constructor(props: IDisclaimerProps & IWizardPageProps & FormikProps<IJob>) {
    super(props);
  }

  public validate(values: any): void {
    values = {};
    return values;
  }

  public render() {
    return (
      <div className="row">
        <div className="col-md-12">
          <div className="well">
            <p>Alpha implementation</p>
            <p>
              The implementation of the <code>Cloud Foundry Spinnaker</code> code is currently in
              <code>Alpha</code> state.
            </p>
            <p>
              As such it is possible that the behavior of the software will change in ways that are not backwards
              compatible with the current implementation.
            </p>
          </div>
        </div>
      </div>
    );
  }
}

export const CfDisclaimerPage = wizardPage<IDisclaimerProps>(CfDisclaimerWizardPage);
