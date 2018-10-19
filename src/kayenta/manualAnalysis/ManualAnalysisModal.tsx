import * as React from 'react';
import { get } from 'lodash';
import { Formik, Form, Field, FormikProps, FormikErrors, FormikActions } from 'formik';
import { Modal } from 'react-bootstrap';

import {
  noop,
  IModalComponentProps,
  ReactModal,
  Application,
  ModalClose,
  SubmitButton,
  TetheredSelect,
  HelpField,
  MapEditor,
} from '@spinnaker/core';

import { ICanaryConfigSummary, IKayentaAccount, KayentaAccountType, ICanaryExecutionRequest } from '../domain';
import { CanaryScores } from '../components/canaryScores';
import { startCanaryRun } from '../service/canaryRun.service';

const RESOURCE_TYPES = {
  prometheus: [
    { label: '', value: '' },
    { label: 'gce_instance', value: 'gce_instance' },
    { label: 'aws_ec2_instance', value: 'aws_ec2_instance' },
  ],
  stackdriver: [
    { label: 'gce_instance', value: 'gce_instance' },
    { label: 'aws_ec2_instance', value: 'aws_ec2_instance' },
    { label: 'gae_app', value: 'gae_app' },
    { label: 'k8s_container', value: 'k8s_container' },
    { label: 'k8s_pod', value: 'k8s_pod' },
    { label: 'k8s_node', value: 'k8s_node' },
    { label: 'gke_container', value: 'gke_container' },
    { label: 'https_lb_rule', value: 'https_lb_rule' },
    { label: 'global', value: 'global' },
  ],
};

export interface IManualAnalysisModalProps extends IModalComponentProps {
  title: string;
  application: Application;
  accounts: IKayentaAccount[];
}

export interface IManualAnalysisModalState {
  showAllControlLocations: boolean;
  showAllExperimentLocations: boolean;
}

export interface IManualAnalysisModalFormProps {
  configId: string;
  startTime: string;
  endTime: string;
  step: string;
  baselineScope: string;
  canaryScope: string;
  baselineLocation: string;
  canaryLocation: string;
  extendedScopeParams: { [key: string]: string };
  resourceType: string;
  marginalThreshold: string;
  passThreshold: string;
  metricsAccountName: string;
  storageAccountName: string;
}

const transformFormPropsToExecutionRequest = (
  values: IManualAnalysisModalFormProps,
  accounts: IKayentaAccount[],
): ICanaryExecutionRequest => {
  const step = parseInt(values.step, 10);
  const pass = parseInt(values.passThreshold, 10);
  const marginal = parseInt(values.marginalThreshold, 10);

  const metricsAccount = accounts.find(({ name }) => values.metricsAccountName === name);

  const accountSpecificParams: { [param: string]: string } = {};

  if (values.resourceType) {
    accountSpecificParams.resourceType = values.resourceType;
  }

  if (metricsAccount.type === 'atlas') {
    accountSpecificParams.type = 'cluster';
  }

  const extendedScopeParams = { ...values.extendedScopeParams, ...accountSpecificParams };

  return {
    scopes: {
      default: {
        controlScope: {
          scope: values.baselineScope,
          start: values.startTime,
          end: values.endTime,
          location: values.baselineLocation,
          step,
          extendedScopeParams,
        },
        experimentScope: {
          scope: values.canaryScope,
          start: values.startTime,
          end: values.endTime,
          location: values.canaryLocation,
          step,
          extendedScopeParams,
        },
      },
    },
    thresholds: {
      pass,
      marginal,
    },
  };
};

const initialValues: IManualAnalysisModalFormProps = {
  configId: null,
  startTime: '',
  endTime: '',
  step: '',
  baselineScope: '',
  canaryScope: '',
  baselineLocation: '',
  canaryLocation: '',
  extendedScopeParams: {},
  resourceType: '',
  marginalThreshold: '',
  passThreshold: '',
  metricsAccountName: '',
  storageAccountName: '',
};

export class ManualAnalysisModal extends React.Component<IManualAnalysisModalProps, IManualAnalysisModalState> {
  public static defaultProps: Partial<IManualAnalysisModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public state: IManualAnalysisModalState = {
    showAllControlLocations: false,
    showAllExperimentLocations: false,
  };

  public static show(props: IManualAnalysisModalProps): Promise<any> {
    const modalProps = { dialogClassName: 'modal-lg' };
    return ReactModal.show(ManualAnalysisModal, props, modalProps);
  }

  constructor(props: IManualAnalysisModalProps) {
    super(props);
  }

  private validate = (values: IManualAnalysisModalFormProps) => {
    const errors = {} as FormikErrors<IManualAnalysisModalFormProps>;

    if (!values.configId) {
      errors.configId = 'You must choose a canary config';
    }

    if (!values.startTime) {
      errors.startTime = 'You must provide a start time';
    }

    if (!values.endTime) {
      errors.endTime = 'You must provide an end time';
    }

    if (!values.step) {
      errors.step = 'You must provide a step value';
    } else if (parseInt(values.step, 10) < 1) {
      errors.step = 'Invalid step value';
    }

    if (!values.baselineScope) {
      errors.baselineScope = 'You must provide a baseline scope';
    }

    if (!values.canaryScope) {
      errors.canaryScope = 'You must provide a canary scope';
    }

    if (!values.baselineLocation) {
      errors.baselineLocation = 'You must provide a baseline location';
    }

    if (!values.canaryLocation) {
      errors.canaryLocation = 'You must provide a canary location';
    }

    const marginalThreshold = parseInt(values.marginalThreshold, 10);
    const passThreshold = parseInt(values.passThreshold, 10);

    if (!values.marginalThreshold) {
      errors.marginalThreshold = 'You must provide a marginal score threshold';
    } else if (marginalThreshold > passThreshold || marginalThreshold < 1) {
      errors.marginalThreshold = 'Invalid marginal score threshold';
    }

    if (!values.passThreshold) {
      errors.passThreshold = 'You must provide a passing score threshold';
    } else if (passThreshold < marginalThreshold || passThreshold > 100) {
      errors.passThreshold = 'Invalid passing score threshold';
    }

    if (!values.metricsAccountName) {
      errors.metricsAccountName = 'You must choose a metrics account';
    }

    if (!values.storageAccountName) {
      errors.storageAccountName = 'You must choose a storage account';
    }

    return errors;
  };

  private submit = (values: IManualAnalysisModalFormProps, actions: FormikActions<IManualAnalysisModalFormProps>) => {
    const { configId, metricsAccountName, storageAccountName } = values;
    const { application, accounts } = this.props;
    const executionRequest = transformFormPropsToExecutionRequest(values, accounts);

    startCanaryRun(configId, executionRequest, {
      application: application.name,
      metricsAccountName,
      storageAccountName,
    })
      .then(() => {
        actions.setSubmitting(false);
        actions.setStatus({ succeeded: true });
      })
      .catch(error => {
        actions.setSubmitting(false);
        actions.setStatus({ succeeded: false, error });
      });
  };

  public render() {
    const { dismissModal, title, application, accounts } = this.props;
    const { showAllControlLocations, showAllExperimentLocations } = this.state;

    const canaryConfigs: ICanaryConfigSummary[] = application.getDataSource('canaryConfigs').data;
    const metricsStores = accounts.filter(({ supportedTypes }) =>
      supportedTypes.includes(KayentaAccountType.MetricsStore),
    );
    const objectStores = accounts.filter(({ supportedTypes }) =>
      supportedTypes.includes(KayentaAccountType.ObjectStore),
    );
    // TODO: support a scope name besides 'default'.
    const showAdvancedSettings = metricsStores.length > 1 || objectStores.length > 1;

    return (
      <Formik
        initialValues={{
          ...initialValues,
          metricsAccountName: get(metricsStores[0], 'name', ''),
          storageAccountName: get(objectStores[0], 'name', ''),
        }}
        onSubmit={this.submit}
        validate={this.validate}
        render={({
          values,
          touched,
          isValid,
          isSubmitting,
          status,
          setFieldValue,
        }: FormikProps<IManualAnalysisModalFormProps>) => {
          if (get(status, 'succeeded') === true) {
            return (
              <>
                <ModalClose dismiss={dismissModal} />
                <Modal.Header>{<h3>Analysis Started</h3>}</Modal.Header>
                <Modal.Body>
                  <p>Analysis started — check the list of reports in a few minutes to view results</p>
                </Modal.Body>
                <Modal.Footer>
                  <button className="btn btn-primary" onClick={dismissModal} type="button">
                    Got it
                  </button>
                </Modal.Footer>
              </>
            );
          }

          const selectedMetricsStore = metricsStores.find(({ name }) => values.metricsAccountName === name);
          const selectedObjectStore = objectStores.find(({ name }) => values.storageAccountName === name);

          const recommendedLocations = get(selectedMetricsStore, 'recommendedLocations', []);
          const locations = get(selectedMetricsStore, 'locations', []);

          const hasLocationChoices = recommendedLocations.length > 0 || locations.length > 0;

          return (
            <Form className="form-horizontal">
              <ModalClose dismiss={dismissModal} />
              <Modal.Header>{title && <h3>{title}</h3>}</Modal.Header>
              <Modal.Body>
                <div className="row">
                  <div className="col-md-12">
                    <div className="container-fluid form-horizontal">
                      <h5>Analysis Configuration</h5>
                      <div className="horizontal-rule" />
                      <div className="form-group">
                        <div className="col-md-3 sm-label-right">Config Name</div>
                        <div className="col-md-7">
                          <TetheredSelect
                            value={values.configId}
                            options={canaryConfigs.map(({ id, name }) => ({ label: name, value: id }))}
                            onChange={(item: { label: string; value: string }) =>
                              setFieldValue('configId', (item && item.value) || null)
                            }
                          />
                        </div>
                      </div>
                      <div className="form-group">
                        <div className="col-md-3 sm-label-right">
                          Start Time <HelpField id="pipeline.config.canary.startTimeIso" />
                        </div>
                        <div className="col-md-7">
                          <Field className="form-control input-sm" name="startTime" required={touched.startTime} />
                        </div>
                      </div>
                      <div className="form-group">
                        <div className="col-md-3 sm-label-right">
                          End Time <HelpField id="pipeline.config.canary.endTimeIso" />
                        </div>
                        <div className="col-md-7">
                          <Field className="form-control input-sm" name="endTime" required={touched.endTime} />
                        </div>
                      </div>
                      <div className="form-group">
                        <div className="col-md-3 sm-label-right">Step</div>
                        <div className="col-md-7">
                          <Field
                            type="number"
                            style={{ width: '60px', display: 'inline-block' }}
                            className="form-control input-sm"
                            name="step"
                            required={touched.step}
                          />
                          <span className="form-control-static"> seconds</span>
                        </div>
                      </div>
                      <h5>Baseline + Canary Pair</h5>
                      <div className="horizontal-rule" />
                      <div className="form-group">
                        <div className="col-md-3 sm-label-right">
                          Baseline <HelpField id="pipeline.config.canary.baselineGroup" />
                        </div>
                        <div className="col-md-7">
                          <Field
                            className="form-control input-sm"
                            name="baselineScope"
                            required={touched.baselineScope}
                          />
                        </div>
                      </div>
                      <div className="form-group">
                        <div className="col-md-3 sm-label-right">
                          Baseline Location <HelpField id="pipeline.config.canary.baselineLocation" />
                        </div>
                        <div className="col-md-7">
                          <LocationField
                            hasLocationChoices={hasLocationChoices}
                            showAll={showAllControlLocations}
                            recommendedLocations={recommendedLocations}
                            locations={locations}
                            value={values.baselineLocation}
                            onChange={location => setFieldValue('baselineLocation', location)}
                            onShowAllChange={showAll => this.setState({ showAllControlLocations: showAll })}
                            input={
                              <Field
                                className="form-control input-sm"
                                name="baselineLocation"
                                required={touched.baselineLocation}
                              />
                            }
                          />
                        </div>
                      </div>
                      <div className="form-group">
                        <div className="col-md-3 sm-label-right">
                          Canary <HelpField id="pipeline.config.canary.canaryGroup" />
                        </div>
                        <div className="col-md-7">
                          <Field className="form-control input-sm" name="canaryScope" required={touched.canaryScope} />
                        </div>
                      </div>
                      <div className="form-group">
                        <div className="col-md-3 sm-label-right">
                          Canary Location <HelpField id="pipeline.config.canary.canaryLocation" />
                        </div>
                        <div className="col-md-7">
                          <LocationField
                            hasLocationChoices={hasLocationChoices}
                            showAll={showAllExperimentLocations}
                            recommendedLocations={recommendedLocations}
                            locations={locations}
                            value={values.canaryLocation}
                            onChange={location => setFieldValue('canaryLocation', location)}
                            onShowAllChange={showAll => this.setState({ showAllExperimentLocations: showAll })}
                            input={
                              <Field
                                className="form-control input-sm"
                                name="canaryLocation"
                                required={touched.canaryLocation}
                              />
                            }
                          />
                        </div>
                      </div>
                      <h5>Metric Scope</h5>
                      <div className="horizontal-rule" />
                      {RESOURCE_TYPES.hasOwnProperty(get(selectedMetricsStore, 'type')) && (
                        <div className="form-group">
                          <div className="col-md-3 sm-label-right">Resource Type</div>
                          <div className="col-md-7">
                            <TetheredSelect
                              value={values.resourceType}
                              options={RESOURCE_TYPES[selectedMetricsStore.type as keyof typeof RESOURCE_TYPES]}
                              onChange={(item: { label: string; value: string }) =>
                                setFieldValue('resourceType', (item && item.value) || '')
                              }
                            />
                          </div>
                        </div>
                      )}
                      <div className="form-group">
                        <div className="col-md-3 sm-label-right">
                          Extended Params <HelpField id="pipeline.config.canary.extendedScopeParams" />
                        </div>
                        <div className="col-md-7">
                          <MapEditor
                            model={values.extendedScopeParams}
                            onChange={model => setFieldValue('extendedScopeParams', model)}
                            hiddenKeys={['resourceType']}
                          />
                        </div>
                      </div>
                      <h5>Scoring Thresholds</h5>
                      <div className="horizontal-rule" />
                      <CanaryScores
                        onChange={({ unhealthyScore, successfulScore }) => {
                          setFieldValue('marginalThreshold', unhealthyScore);
                          setFieldValue('passThreshold', successfulScore);
                        }}
                        successfulHelpFieldId="pipeline.config.canary.passingScore"
                        successfulLabel="Pass"
                        successfulScore={values.passThreshold}
                        unhealthyHelpFieldId="pipeline.config.canary.marginalScore"
                        unhealthyLabel="Marginal"
                        unhealthyScore={values.marginalThreshold}
                      />
                      {showAdvancedSettings && (
                        <AdvancedSettings
                          metricsStores={metricsStores}
                          selectedMetricsStore={selectedMetricsStore}
                          objectStores={objectStores}
                          selectedObjectStore={selectedObjectStore}
                          onChange={setFieldValue}
                        />
                      )}
                    </div>
                  </div>
                </div>
                {get(status, 'error') && (
                  <div className="row">
                    <div className="col-md-7 col-md-offset-3">
                      <div className="well-compact alert alert-danger">
                        <b>There was a problem starting your analysis:</b>
                        <span>{JSON.stringify(status.error.data)}</span>
                      </div>
                    </div>
                  </div>
                )}
              </Modal.Body>
              <Modal.Footer>
                <button className="btn btn-default" onClick={dismissModal} type="button">
                  Cancel
                </button>
                <SubmitButton
                  isDisabled={!isValid}
                  submitting={isSubmitting}
                  isFormSubmit={true}
                  label="Start analysis"
                />
              </Modal.Footer>
            </Form>
          );
        }}
      />
    );
  }
}

const combineLocations = (includeAll: boolean, recommendedLocations: string[], locations: string[]) => {
  if (includeAll) {
    return [...new Set(recommendedLocations.concat(locations))];
  }

  return recommendedLocations.length > 0 ? recommendedLocations : locations;
};

interface ILocationFieldProps {
  hasLocationChoices: boolean;
  showAll: boolean;
  recommendedLocations: string[];
  locations: string[];
  value: string;
  onChange: (location: string) => any;
  onShowAllChange: (showAll: boolean) => any;
  input: JSX.Element;
}

const LocationField = ({
  hasLocationChoices,
  showAll,
  recommendedLocations,
  locations,
  value,
  onChange,
  onShowAllChange,
  input,
}: ILocationFieldProps) => {
  if (!hasLocationChoices) {
    return input;
  }

  const combinedLocations = combineLocations(showAll, recommendedLocations, locations);
  const options = combinedLocations.map(location => ({ label: location, value: location }));

  return (
    <>
      <TetheredSelect
        clearable={false}
        value={value}
        options={options}
        onChange={(item: { label: string; value: string }) => onChange((item && item.value) || '')}
      />
      {recommendedLocations.length > 0 &&
        locations.length > 0 && (
          <div className="pull-right">
            <button
              type="button"
              className="link"
              onClick={() => {
                onShowAllChange(!showAll);
                if (!combineLocations(!showAll, recommendedLocations, locations).includes(value)) {
                  onChange('');
                }
              }}
            >
              {showAll ? 'Only show recommended locations' : 'Show all locations'}
            </button>
          </div>
        )}
    </>
  );
};

interface IAdvancedSettingsProps {
  metricsStores: IKayentaAccount[];
  objectStores: IKayentaAccount[];
  selectedMetricsStore: IKayentaAccount;
  selectedObjectStore: IKayentaAccount;
  onChange: (field: 'metricsAccountName' | 'storageAccountName', value: string) => any;
}

const AdvancedSettings = ({
  metricsStores,
  selectedMetricsStore,
  objectStores,
  selectedObjectStore,
  onChange,
}: IAdvancedSettingsProps) => {
  return (
    <>
      <h5>Advanced Settings</h5>
      <div className="horizontal-rule" />
      <div className="form-group">
        <div className="col-md-3 sm-label-right">
          Metrics Account <HelpField id="pipeline.config.metricsAccount" />
        </div>
        <div className="col-md-7">
          <TetheredSelect
            clearable={false}
            value={get(selectedMetricsStore, 'name')}
            options={metricsStores.map(({ name }) => ({ label: name, value: name }))}
            onChange={(item: { label: string; value: string }) =>
              onChange('metricsAccountName', (item && item.value) || '')
            }
          />
        </div>
      </div>
      <div className="form-group">
        <div className="col-md-3 sm-label-right">
          Storage Account <HelpField id="pipeline.config.storageAccount" />
        </div>
        <div className="col-md-7">
          <TetheredSelect
            clearable={false}
            value={get(selectedObjectStore, 'name')}
            options={objectStores.map(({ name }) => ({ label: name, value: name }))}
            onChange={(item: { label: string; value: string }) =>
              onChange('storageAccountName', (item && item.value) || '')
            }
          />
        </div>
      </div>
    </>
  );
};
