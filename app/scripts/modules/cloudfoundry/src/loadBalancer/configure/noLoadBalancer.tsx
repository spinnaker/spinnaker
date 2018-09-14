import * as React from 'react';

import { FormikProps } from 'formik';

import { wizardPage, IWizardPageProps } from '@spinnaker/core';

import { ICloudFoundryLoadBalancerUpsertCommand } from 'cloudfoundry/domain';

class NoLoadBalancerDetailsImpl extends React.Component<
  IWizardPageProps & FormikProps<ICloudFoundryLoadBalancerUpsertCommand>
> {
  public static LABEL = 'Message';

  constructor(props: IWizardPageProps & FormikProps<ICloudFoundryLoadBalancerUpsertCommand>) {
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
            <p>Spinnaker cannot create a load balancer for Cloud Foundry</p>
            <p>
              A Spinnaker load balancer maps to a Cloud Foundry route, which is specified in the configuration of a{' '}
              <code>Server Group</code>
            </p>
            <p>
              If a route does not exist when a <code>Server Group</code> is deployed, it will be created. It will then
              be visible as a load balancer within Spinnaker.
            </p>
            <p>
              <a href="https://docs.cloudfoundry.org/devguide/deploy-apps/routes-domains.html" target="_blank">
                Cloud Foundry Documentation
              </a>
            </p>
          </div>
        </div>
      </div>
    );
  }
}

export const NoLoadBalancerDetails = wizardPage<IWizardPageProps>(NoLoadBalancerDetailsImpl);
