import { Form, FormikContext } from 'formik';
import React from 'react';
import { Modal } from 'react-bootstrap';

import {
  Application,
  FormikFormField,
  ICapacity,
  IModalComponentProps,
  MinMaxDesiredChanges,
  ModalClose,
  NumberInput,
  PlatformHealthOverride,
  ReactInjector,
  SpinFormik,
  TaskMonitorWrapper,
  UserVerification,
  ValidationMessage,
} from '@spinnaker/core';
import { ITitusServerGroup } from '../../../domain';

import { useTaskMonitor } from './useTaskMonitor';

const { useState, useEffect, useMemo } = React;

export interface ITitusResizeServerGroupModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: ITitusServerGroup;
}

interface ITitusResizeServerGroupCommand {
  capacity: ICapacity;
  serverGroupName: string;
  instances: number;
  interestingHealthProviderNames: string[];
  region: string;
}

function surfacedErrorMessage(formik: FormikContext<ITitusResizeServerGroupCommand>) {
  const capacityErrors = formik.errors.capacity || ({} as any);
  const { min, max, desired } = capacityErrors;
  return [min, max, desired].find((x) => !!x);
}

function SimpleMode({ formik, serverGroup, toggleMode }: IAdvancedModeProps) {
  useEffect(() => {
    formik.setFieldValue('capacity.min', formik.values.capacity.desired);
    formik.setFieldValue('capacity.max', formik.values.capacity.desired);
  }, [formik.values.capacity.desired]);

  const errorMessage = surfacedErrorMessage(formik);

  return (
    <div>
      <p>Sets min, max, and desired instance counts to the same value.</p>

      <p>
        To allow autoscaling, use the{' '}
        <a className="clickable" onClick={toggleMode}>
          Advanced Mode
        </a>
        .
      </p>

      <div className="form-group row">
        <div className="col-md-3 sm-label-right">Current size</div>
        <div className="col-md-4">
          <div className="horizontal middle">
            <input
              type="number"
              className="NumberInput form-control"
              value={serverGroup.capacity.desired}
              disabled={true}
            />
            <div className="sp-padding-xs-xaxis">instances</div>
          </div>
        </div>
      </div>

      <div className="form-group">
        <div className="col-md-3 sm-label-right">Resize to</div>
        <div className="col-md-4">
          <div className="horizontal middle">
            <FormikFormField
              name="capacity.desired"
              input={(props) => <NumberInput {...props} min={0} />}
              layout={({ input }) => <>{input}</>}
              touched={true}
              onChange={() => {}}
            />
            <div className="sp-padding-xs-xaxis">instances</div>
          </div>
        </div>
      </div>

      {!!errorMessage && (
        <div className="col-md-offset-3 col-md-9">
          <ValidationMessage message={errorMessage} type="error" />
        </div>
      )}

      <div className="form-group">
        <div className="col-md-3 sm-label-right">Changes</div>
        <div className="col-md-9 sm-control-field">
          <MinMaxDesiredChanges current={serverGroup.capacity} next={formik.values.capacity} />
        </div>
      </div>
    </div>
  );
}

interface IAdvancedModeProps {
  formik: FormikContext<ITitusResizeServerGroupCommand>;
  serverGroup: ITitusServerGroup;
  toggleMode: () => void;
}

function AdvancedMode({ formik, serverGroup, toggleMode }: IAdvancedModeProps) {
  const { min, max } = formik.values.capacity || ({} as any);

  const DisabledNumberField = ({ value }: { value: string | number }) => (
    <div className="col-md-2">
      <input className="NumberInput form-control" type="number" disabled={true} value={value} />
    </div>
  );
  const errorMessage = surfacedErrorMessage(formik);

  return (
    <div>
      <p>Sets up auto-scaling for this server group.</p>
      <p>
        To disable auto-scaling, use the{' '}
        <a className="clickable" onClick={toggleMode}>
          Simple Mode
        </a>
        .
      </p>

      <div className="form-group bold">
        <div className="col-md-2 col-md-offset-3">Min</div>
        <div className="col-md-2">Max</div>
        <div className="col-md-2">Desired</div>
      </div>

      <div className="form-group">
        <div className="col-md-3 sm-label-right">Current</div>
        <DisabledNumberField value={serverGroup.capacity.min} />
        <DisabledNumberField value={serverGroup.capacity.max} />
        <DisabledNumberField value={serverGroup.capacity.desired} />
      </div>

      <div className="form-group">
        <div className="col-md-3 sm-label-right">Resize to</div>
        <div className="col-md-2">
          <FormikFormField
            name="capacity.min"
            input={(props) => <NumberInput {...props} min={0} max={max} />}
            layout={({ input }) => <>{input}</>}
            touched={true}
          />
        </div>

        <div className="col-md-2">
          <FormikFormField
            name="capacity.max"
            input={(props) => <NumberInput {...props} min={min} />}
            layout={({ input }) => <>{input}</>}
            touched={true}
          />
        </div>

        <div className="col-md-2">
          <FormikFormField
            name="capacity.desired"
            input={(props) => <NumberInput {...props} min={min} max={max} />}
            layout={({ input }) => <>{input}</>}
            touched={true}
          />
        </div>
      </div>

      {!!errorMessage && (
        <div className="col-md-offset-3 col-md-9">
          <ValidationMessage message={errorMessage} type="error" />
        </div>
      )}

      <div className="form-group">
        <div className="col-md-3 sm-label-right">Changes</div>
        <div className="col-md-9 sm-control-field">
          <MinMaxDesiredChanges current={serverGroup.capacity} next={formik.values.capacity} />
        </div>
      </div>
    </div>
  );
}

function validateResizeCommand(values: ITitusResizeServerGroupCommand) {
  const { min, max, desired } = values.capacity;
  const capacityErrors = {} as any;

  // try to only show one error message at a time
  if (min > max) {
    capacityErrors.min = capacityErrors.max = 'Min cannot be larger than Max';
  } else if (desired < min) {
    capacityErrors.desired = capacityErrors.min = 'Desired cannot be smaller than Min';
  } else if (desired > max) {
    capacityErrors.desired = capacityErrors.max = 'Desired cannot be larger than Max';
  }

  if (Object.keys(capacityErrors).length) {
    return { capacity: capacityErrors };
  }

  return {};
}

export function TitusResizeServerGroupModal(props: ITitusResizeServerGroupModalProps) {
  const { serverGroup, application, dismissModal } = props;

  const initialAdvancedMode = useMemo(() => {
    const { min, max, desired } = serverGroup.capacity;
    return desired !== max || desired !== min;
  }, []);
  const [advancedMode, setAdvancedMode] = useState(initialAdvancedMode);

  const platformHealthOnlyShowOverride =
    application.attributes && application.attributes.platformHealthOnlyShowOverride;
  const [verified, setVerified] = useState<boolean>();

  const taskMonitor = useTaskMonitor(
    {
      application,
      title: `Resizing ${serverGroup.name}`,
      onTaskComplete: () => application.getDataSource('serverGroups').refresh(true),
    },
    dismissModal,
  );
  const submit = (command: ITitusResizeServerGroupCommand) =>
    taskMonitor.submit(() => ReactInjector.serverGroupWriter.resizeServerGroup(serverGroup, application, command));

  const initialValues = { capacity: serverGroup.capacity } as ITitusResizeServerGroupCommand;

  return (
    <>
      <TaskMonitorWrapper monitor={taskMonitor} />

      <SpinFormik<ITitusResizeServerGroupCommand>
        initialValues={initialValues}
        validate={validateResizeCommand}
        onSubmit={submit}
        render={(formik) => {
          return (
            <>
              <ModalClose dismiss={dismissModal} />
              <Modal.Header>
                <Modal.Title>Resize {serverGroup.name}</Modal.Title>
              </Modal.Header>

              <Modal.Body>
                <Form className="form-horizontal">
                  {advancedMode ? (
                    <AdvancedMode formik={formik} serverGroup={serverGroup} toggleMode={() => setAdvancedMode(false)} />
                  ) : (
                    <SimpleMode formik={formik} serverGroup={serverGroup} toggleMode={() => setAdvancedMode(true)} />
                  )}

                  {platformHealthOnlyShowOverride && (
                    <PlatformHealthOverride
                      interestingHealthProviderNames={formik.values.interestingHealthProviderNames}
                      platformHealthType="Titus"
                      showHelpDetails={true}
                      onChange={(names) =>
                        formik.setFieldValue('interestingHealthProviderNames', names ? names : undefined)
                      }
                    />
                  )}
                </Form>
              </Modal.Body>

              <Modal.Footer>
                <UserVerification account={serverGroup.account} onValidChange={setVerified} />

                <button className="btn btn-default" onClick={dismissModal}>
                  Cancel
                </button>

                <button
                  type="submit"
                  disabled={!verified || !formik.isValid}
                  className="btn btn-primary"
                  onClick={() => submit(formik.values)}
                >
                  Submit
                </button>
              </Modal.Footer>
            </>
          );
        }}
      />
    </>
  );
}
