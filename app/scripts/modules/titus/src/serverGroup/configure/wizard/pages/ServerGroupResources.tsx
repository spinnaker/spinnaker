import * as React from 'react';
import { FormikErrors } from 'formik';
import Select, { Option } from 'react-select';

import { IWizardPageProps, wizardPage, HelpField } from '@spinnaker/core';

import { ITitusServerGroupCommand } from '../../../configure/serverGroupConfiguration.service';

const mountPermOptions = [
  { label: 'Read and Write', value: 'RW' },
  { label: 'Read Only', value: 'RO' },
  { label: 'Write Only', value: 'WO' },
];

export type IServerGroupResourcesProps = IWizardPageProps<ITitusServerGroupCommand>;

class ServerGroupResourcesImpl extends React.Component<IServerGroupResourcesProps> {
  public static LABEL = 'Resources';

  public validate(values: ITitusServerGroupCommand) {
    const errors: FormikErrors<ITitusServerGroupCommand> = {};

    if (!values.resources || typeof values.resources.cpu === 'string') {
      errors.resources = 'CPU is required.';
    }
    if (!values.resources || typeof values.resources.memory === 'string') {
      errors.resources = 'Memory is required.';
    }
    if (!values.resources || typeof values.resources.disk === 'string') {
      errors.resources = 'Disk is required.';
    }
    if (!values.resources || typeof values.resources.networkMbps === 'string') {
      errors.resources = 'Network is required.';
    }

    return errors;
  }

  public render() {
    const { setFieldValue, values } = this.props.formik;
    return (
      <div className="clearfix">
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>CPU(s)</b>
          </div>
          <div className="col-md-3">
            <input
              type="number"
              className="form-control input-sm"
              value={values.resources.cpu}
              onChange={e =>
                setFieldValue('resources', {
                  ...values.resources,
                  ...{ cpu: Number.parseInt(e.target.value, 10) || e.target.value },
                })
              }
              required={true}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>Memory (MB)</b>
          </div>
          <div className="col-md-3">
            <input
              type="number"
              className="form-control input-sm"
              value={values.resources.memory}
              onChange={e =>
                setFieldValue('resources', {
                  ...values.resources,
                  ...{ memory: Number.parseInt(e.target.value, 10) || e.target.value },
                })
              }
              required={true}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>Disk (MB)</b>
          </div>
          <div className="col-md-3">
            <input
              type="number"
              className="form-control input-sm"
              value={values.resources.disk}
              onChange={e =>
                setFieldValue('resources', {
                  ...values.resources,
                  ...{ disk: Number.parseInt(e.target.value, 10) || e.target.value },
                })
              }
              required={true}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>Network (Mbps)</b>
            <HelpField id="titus.deploy.network" />
          </div>
          <div className="col-md-3">
            <input
              type="number"
              className="form-control input-sm"
              value={values.resources.networkMbps}
              onChange={e =>
                setFieldValue('resources', {
                  ...values.resources,
                  ...{ networkMbps: Number.parseInt(e.target.value, 10) || e.target.value },
                })
              }
              required={true}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>Gpu</b>
            <HelpField id="titus.deploy.gpu" />
          </div>
          <div className="col-md-3">
            <input
              type="number"
              className="form-control input-sm"
              value={values.resources.gpu}
              onChange={e =>
                setFieldValue('resources', {
                  ...values.resources,
                  ...{ gpu: Number.parseInt(e.target.value, 10) || e.target.value },
                })
              }
            />
          </div>
        </div>
        <hr />

        <h4>
          <b>
            Elastic File System Options <HelpField id="titus.deploy.efs" />
          </b>
        </h4>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>Mount Permission </b>
            <HelpField id="titus.deploy.mountPermissions" />
          </div>
          <div className="col-md-8">
            <Select
              value={values.efs.mountPerm}
              clearable={false}
              options={mountPermOptions}
              onChange={(option: Option) => setFieldValue('efs', { ...values.efs, ...{ mountPerm: option.value } })}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>Mount Point </b>
            <HelpField id="titus.deploy.mountPoint" />
          </div>
          <div className="col-md-8">
            <input
              type="text"
              className="form-control input-sm no-spel"
              value={values.efs.mountPoint}
              onChange={e => setFieldValue('efs', { ...values.efs, ...{ mountPoint: e.target.value } })}
            />
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>EFS ID </b>
            <HelpField id="titus.deploy.efsId" />
          </div>
          <div className="col-md-8">
            <input
              type="text"
              className="form-control input-sm no-spel"
              value={values.efs.efsId}
              onChange={e => setFieldValue('efs', { ...values.efs, ...{ efsId: e.target.value } })}
            />
          </div>
          <div className="col-md-offset-3 col-md-8" />
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            <b>EFS Relative Mount Point </b>
            <HelpField id="titus.deploy.efsRelativeMountPoint" />
          </div>
          <div className="col-md-8">
            <input
              type="text"
              className="form-control input-sm no-spel"
              value={values.efs.efsRelativeMountPoint}
              onChange={e => setFieldValue('efs', { ...values.efs, ...{ efsRelativeMountPoint: e.target.value } })}
            />
          </div>
        </div>
      </div>
    );
  }
}

export const ServerGroupResources = wizardPage(ServerGroupResourcesImpl);
