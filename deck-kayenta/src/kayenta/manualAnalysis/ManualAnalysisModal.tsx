import { Field, Form, Formik, FormikActions, FormikErrors, FormikProps, FormikTouched } from 'formik';
import { get } from 'lodash';
import * as React from 'react';
import { Modal } from 'react-bootstrap';

import {
  Application,
  HelpField,
  HoverablePopover,
  IModalComponentProps,
  MapEditor,
  ModalClose,
  noop,
  ReactModal,
  SubmitButton,
  TetheredSelect,
} from '@spinnaker/core';

import { CanaryScores } from '../components/canaryScores';
import { ICanaryConfigSummary, ICanaryExecutionRequest, IKayentaAccount, KayentaAccountType } from '../domain';
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
  initialValues?: IManualAnalysisModalFormProps;
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

  if (metricsAccount.type === 'atlas' && !values.extendedScopeParams?.type) {
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

const defaultValues: IManualAnalysisModalFormProps = {
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
    initialValues: defaultValues,
  };

  public static show(props: IManualAnalysisModalProps): Promise<any> {
    const modalProps = { dialogClassName: 'modal-lg' };
    return ReactModal.show(ManualAnalysisModal, props, modalProps);
  }

  constructor(props: IManualAnalysisModalProps) {
    super(props);
    const { initialValues, accounts } = props;
    const selectedMetricsStore = accounts.find(({ name }) => initialValues.metricsAccountName === name);
    const recommendedLocations = selectedMetricsStore?.recommendedLocations ?? [];
    this.state = {
      showAllControlLocations: !recommendedLocations.includes(initialValues.baselineLocation),
      showAllExperimentLocations: !recommendedLocations.includes(initialValues.canaryLocation),
    };
  }

  private validate = (values: IManualAnalysisModalFormProps) => {
    const errors = {} as FormikErrors<IManualAnalysisModalFormProps>;
    let startTime, endTime;

    if (!values.configId) {
      errors.configId = 'You must choose a canary config';
    }

    if (!values.startTime) {
      errors.startTime = 'You must provide a start time';
    } else {
      try {
        startTime = new Date(values.startTime).getTime();
      } catch (e) {
        errors.startTime = 'Invalid start time. Time must be in ISO-8601 format, e.g. 2018-07-12T22:28:29Z';
      }
    }

    if (!values.endTime) {
      errors.endTime = 'You must provide an end time';
    } else {
      try {
        endTime = new Date(values.endTime).getTime();
      } catch (e) {
        errors.endTime = 'Invalid start time. Time must be in ISO-8601 format, e.g. 2018-07-12T22:28:29Z';
      }
    }

    if (startTime && endTime && endTime <= startTime) {
      errors.endTime = 'End time must be after start time';
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
    } else if (marginalThreshold > passThreshold) {
      errors.marginalThreshold = 'Marginal threshold must be greater than passing threshold';
    } else if (marginalThreshold < 1) {
      errors.marginalThreshold = 'Marginal threshold must be positive';
    }

    if (!values.passThreshold) {
      errors.passThreshold = 'You must provide a passing score threshold';
    } else if (passThreshold > 100) {
      errors.passThreshold = 'Passing threshold cannot be greater than 100';
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
      .catch((error) => {
        actions.setSubmitting(false);
        actions.setStatus({ succeeded: false, error });
      });
  };

  public render() {
    const { dismissModal, title, application, accounts, initialValues } = this.props;
    const { showAllControlLocations, showAllExperimentLocations } = this.state;

    const canaryConfigs: ICanaryConfigSummary[] = application
      .getDataSource('canaryConfigs')
      .data.slice()
      .sort((a: ICanaryConfigSummary, b: ICanaryConfigSummary) => a.name.localeCompare(b.name));
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
        isInitialValid={!!initialValues.configId} // if we have a config id initially, everything else should be valid
        render={({
          values,
          touched,
          isValid,
          isSubmitting,
          status,
          setFieldValue,
          setFieldTouched,
          errors,
        }: FormikProps<IManualAnalysisModalFormProps>) => {
          if (get(status, 'succeeded') === true) {
            return (
              <>
                <ModalClose dismiss={dismissModal} />
                <Modal.Header>{<h3>Analysis Started</h3>}</Modal.Header>
                <Modal.Body>
                  <p>Analysis started — check the list of reports in a few minutes to view results</p>
                </Modal.Body>
                <Modal.Footer className="horizontal right">
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
          const { canaryLocation, baselineLocation } = initialValues;
          const locations = get(selectedMetricsStore, 'locations', []);
          // Almost every report generated for Atlas uses a simple region for its location. On the backend, Kayenta
          // performs amazing feats to reconcile the location with an Atlas backend, considering the values of several
          // extended params. We are not going to reverse engineer that logic here in order to pick a predefined value
          // from the dropdown: if a location was valid to generate the report, it'll probably be valid when we
          // regenerate it.
          if (canaryLocation && !locations.includes(canaryLocation)) {
            locations.push(canaryLocation);
          }
          if (baselineLocation && !locations.includes(baselineLocation)) {
            locations.push(baselineLocation);
          }

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
                          <ValidatedField errors={errors} touched={touched} name="startTime" />
                        </div>
                      </div>
                      <div className="form-group">
                        <div className="col-md-3 sm-label-right">
                          End Time <HelpField id="pipeline.config.canary.endTimeIso" />
                        </div>
                        <div className="col-md-7">
                          <ValidatedField errors={errors} touched={touched} name="endTime" />
                        </div>
                      </div>
                      <div className="form-group">
                        <div className="col-md-3 sm-label-right">Step</div>
                        <div className="col-md-7">
                          <ValidatedField
                            errors={errors}
                            touched={touched}
                            type="number"
                            style={{ width: '60px', display: 'inline-block' }}
                            name="step"
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
                          <ValidatedField name="baselineScope" errors={errors} touched={touched} />
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
                            field="baselineLocation"
                            errors={errors}
                            touched={touched}
                            onChange={(location) => {
                              setFieldTouched('baselineLocation', true);
                              setFieldValue('baselineLocation', location);
                            }}
                            onShowAllChange={(showAll) => this.setState({ showAllControlLocations: showAll })}
                          />
                        </div>
                      </div>
                      <div className="form-group">
                        <div className="col-md-3 sm-label-right">
                          Canary <HelpField id="pipeline.config.canary.canaryGroup" />
                        </div>
                        <div className="col-md-7">
                          <ValidatedField errors={errors} touched={touched} name="canaryScope" />
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
                            field="canaryLocation"
                            errors={errors}
                            touched={touched}
                            onChange={(location) => {
                              setFieldTouched('canaryLocation', true);
                              setFieldValue('canaryLocation', location);
                            }}
                            onShowAllChange={(showAll) => this.setState({ showAllExperimentLocations: showAll })}
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
                            <ErrorMessage field="resourceType" errors={errors} touched={touched} />
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
                            onChange={(model) => setFieldValue('extendedScopeParams', model)}
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
                        successfulLabel="Pass "
                        successfulScore={values.passThreshold}
                        unhealthyHelpFieldId="pipeline.config.canary.marginalScore"
                        unhealthyLabel="Marginal "
                        unhealthyScore={values.marginalThreshold}
                      />
                      {showAdvancedSettings && (
                        <AdvancedSettings
                          metricsStores={metricsStores}
                          selectedMetricsStore={selectedMetricsStore}
                          objectStores={objectStores}
                          selectedObjectStore={selectedObjectStore}
                          onChange={setFieldValue}
                          errors={errors}
                          touched={touched}
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
              <Modal.Footer className="horizontal right">
                <button className="btn btn-default" onClick={dismissModal} type="button">
                  Cancel
                </button>
                <SubmitFormButton isValid={isValid} isSubmitting={isSubmitting} errors={errors} />
              </Modal.Footer>
            </Form>
          );
        }}
      />
    );
  }
}

const SubmitFormButton = ({
  errors,
  isValid,
  isSubmitting,
}: {
  errors: FormikErrors<any>;
  isValid: boolean;
  isSubmitting: boolean;
}) => {
  const button = (
    <SubmitButton isDisabled={!isValid} submitting={isSubmitting} isFormSubmit={true} label="Start analysis" />
  );
  const errorMessages = Object.values(errors).map((error: string, i) => <li key={i}>{error}</li>);
  if (isValid || !errorMessages.length) {
    return button;
  }

  const popoverComponent = () => (
    <div>
      The following errors must be corrected before submitting:<ul>{errorMessages}</ul>
    </div>
  );
  return (
    <div className="sp-margin-s-left">
      <HoverablePopover Component={popoverComponent}>{button}</HoverablePopover>
    </div>
  );
};

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
  errors: FormikErrors<IManualAnalysisModalFormProps>;
  touched: FormikTouched<IManualAnalysisModalFormProps>;
  field: keyof IManualAnalysisModalFormProps;
}

const LocationField = ({
  hasLocationChoices,
  showAll,
  recommendedLocations,
  locations,
  value,
  onChange,
  onShowAllChange,
  errors,
  touched,
  field,
}: ILocationFieldProps) => {
  if (!hasLocationChoices) {
    return <ValidatedField name={field} errors={errors} touched={touched} />;
  }
  const combinedLocations = combineLocations(showAll, recommendedLocations, locations).sort();
  const options = combinedLocations.map((location) => ({ label: location, value: location }));

  return (
    <>
      <TetheredSelect
        clearable={false}
        value={value}
        options={options}
        onChange={(item: { label: string; value: string }) => onChange((item && item.value) || '')}
      />
      <ErrorMessage field={field} errors={errors} touched={touched} />
      {recommendedLocations.length > 0 && locations.length > 0 && (
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
  errors: FormikErrors<IManualAnalysisModalFormProps>;
  touched: FormikTouched<IManualAnalysisModalFormProps>;
}

const AdvancedSettings = ({
  metricsStores,
  selectedMetricsStore,
  objectStores,
  selectedObjectStore,
  onChange,
  errors,
  touched,
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
          <ErrorMessage field="metricsAccountName" errors={errors} touched={touched} />
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
          <ErrorMessage field="storageAccountName" errors={errors} touched={touched} />
        </div>
      </div>
    </>
  );
};

interface IErrorMessageProps {
  field: keyof IManualAnalysisModalFormProps;
  errors: FormikErrors<IManualAnalysisModalFormProps>;
  touched: FormikTouched<IManualAnalysisModalFormProps>;
}

const ErrorMessage = ({ field, errors, touched }: IErrorMessageProps) => {
  if (!touched[field] || !errors[field]) {
    return null;
  }
  return <div className="error-message">{errors[field]}</div>;
};

export interface IValidatedFieldProps {
  errors: FormikErrors<IManualAnalysisModalFormProps>;
  touched: FormikTouched<IManualAnalysisModalFormProps>;
  name: keyof IManualAnalysisModalFormProps;
  [field: string]: any;
}

const ValidatedField = (props: IValidatedFieldProps) => {
  const { errors, name, touched } = props;
  return (
    <>
      <Field {...props} className="form-control input-sm" required={touched[name]} error={errors[name]} />
      <ErrorMessage field={name} errors={errors} touched={touched} />
    </>
  );
};
