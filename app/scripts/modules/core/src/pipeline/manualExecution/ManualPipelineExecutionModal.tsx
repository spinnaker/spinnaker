import { Form, Formik } from 'formik';
import { assign, clone, compact, extend, get, head, isArray, isEmpty, isEqual, pickBy, uniq } from 'lodash';
import React from 'react';
import { Modal } from 'react-bootstrap';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { CurrentlyRunningExecutions } from './CurrentlyRunningExecutions';
import { DryRun } from './DryRun';
import { NotificationDetails } from './NotificationDetails';
import { Parameters } from './Parameters';
import { PipelineOptions } from './PipelineOptions';
import { StageManualComponents } from './StageManualComponents';
import { ITriggerTemplateComponentProps } from './TriggerTemplate';
import { Triggers } from './Triggers';
import { Application } from '../../application';
import { AuthenticationService } from '../../authentication';
import { SETTINGS } from '../../config/settings';
import { IPipelineTemplateConfig } from '../config/templates';
import { PipelineTemplateReader } from '../config/templates/PipelineTemplateReader';
import {
  IExecution,
  IExecutionTrigger,
  INotification,
  IParameter,
  IPipeline,
  IPipelineCommand,
  IPipelineTrigger,
  IStage,
  ITrigger,
} from '../../domain';
import { ManualExecutionFieldLayout } from './layout/ManualExecutionFieldLayout';
import { ModalClose, SubmitButton } from '../../modal';
import { UrlParser } from '../../navigation/urlParser';
import { AppNotificationsService } from '../../notification/AppNotificationsService';
import {
  FormValidator,
  IModalComponentProps,
  LayoutProvider,
  Markdown,
  ReactModal,
  SpinFormik,
} from '../../presentation';
import { Registry } from '../../registry';
import { ArtifactList } from '../status/ArtifactList';

import './manualPipelineExecution.less';

export interface IManualExecutionModalProps extends IModalComponentProps {
  application: Application;
  currentlyRunningExecutions?: IExecution[];
  pipeline?: IPipeline;
  trigger?: IExecutionTrigger;
}

export interface IManualExecutionModalState {
  applicationNotifications: INotification[];
  currentPipelineExecutions: IExecution[];
  dryRunEnabled?: boolean;
  modalHeader: string;
  pipelineNotifications: INotification[];
  pipelineOptions?: IPipeline[];
  stageComponents: Array<React.ComponentType<ITriggerTemplateComponentProps>>;
  triggerComponent?: React.ComponentType<ITriggerTemplateComponentProps>;
  triggers: ITrigger[];
}

const TRIGGER_FIELDS_TO_EXCLUDE = ['correlationId', 'eventId', 'executionId'];

export class ManualExecutionModal extends React.Component<IManualExecutionModalProps, IManualExecutionModalState> {
  private formikRef = React.createRef<Formik<any>>();
  private destroy$ = new Subject();

  constructor(props: IManualExecutionModalProps) {
    super(props);
    let modalHeader: string;
    if (props.pipeline) {
      modalHeader = 'Select Pipeline';
      if (props.pipeline.triggers) {
        modalHeader = 'Select Execution Parameters';
      } else {
        modalHeader = 'Confirm Execution';
      }
    } else {
      modalHeader = 'Select Pipeline';
    }
    this.state = {
      applicationNotifications: [],
      currentPipelineExecutions: props.currentlyRunningExecutions || [],
      modalHeader,
      pipelineNotifications: [],
      pipelineOptions: [],
      stageComponents: [],
      triggers: [],
    };
  }

  private static getPipelineTriggers(pipeline: IPipeline, trigger: IExecutionTrigger): ITrigger[] {
    if (!isEmpty(pipeline.triggers)) {
      return pipeline.triggers;
    }

    /**
     * If Pipeline B runs as a stage of Pipeline A, we want manual
     * re-runs to behave as though Pipeline B were triggered by Pipeline A,
     * so that artifacts from the prior execution are passed to the re-run
     * as expected, so we shim the trigger.
     */
    if (trigger && trigger.type === 'pipeline' && trigger.parentPipelineStageId) {
      return [
        {
          enabled: true,
          parentExecution: trigger.parentExecution,
          type: trigger.type,
        } as IPipelineTrigger,
      ];
    }

    return [];
  }

  public componentDidMount() {
    const { application, pipeline, trigger } = this.props;
    let pipelineOptions = [];
    let pipelineNotifications: INotification[] = [];
    if (pipeline) {
      pipelineNotifications = pipeline.notifications || [];
      const pipelineTriggers = ManualExecutionModal.getPipelineTriggers(pipeline, trigger);
      const triggers = this.formatTriggers(pipelineTriggers);
      this.updateTriggerOptions(triggers);
    } else {
      pipelineOptions = application.pipelineConfigs.data.filter((c: any) => !c.disabled);
    }

    this.triggerChanged(trigger);

    observableFrom(AppNotificationsService.getNotificationsForApplication(application.name))
      .pipe(takeUntil(this.destroy$))
      .subscribe((notifications) => {
        const applicationNotifications: INotification[] = [];
        Object.keys(notifications)
          .sort()
          .filter((k) => Array.isArray(notifications[k]))
          .forEach((type) => {
            if (isArray(notifications[type])) {
              (notifications[type] as INotification[]).forEach((notification: INotification) => {
                applicationNotifications.push(notification);
              });
            }
          });
        this.setState({ applicationNotifications });
      });
    this.pipelineChanged(pipeline);
    this.setState({
      dryRunEnabled: SETTINGS.feature.dryRunEnabled,
      pipelineOptions,
      pipelineNotifications,
    });
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  public static show(props: any): Promise<IPipelineCommand> {
    const modalProps = {
      dialogClassName: 'manual-execution-dialog ' + 'modal-md',
    };
    return ReactModal.show(ManualExecutionModal, props, modalProps);
  }

  private submit = (values: IPipelineCommand): void => {
    const selectedTrigger: { [key: string]: any } = clone(values.trigger || {});
    const command: { [key: string]: any } = {
      trigger: selectedTrigger,
    };
    const pipeline = values.pipeline;

    if (values.notificationEnabled && values.notification.address) {
      selectedTrigger.notifications = [values.notification];
    }

    // include any extra data populated by trigger manual execution handlers
    extend(selectedTrigger, values.extraFields);

    command.pipelineName = pipeline.name;
    selectedTrigger.type = 'manual';
    selectedTrigger.dryRun = values.dryRun;
    // The description is not part of the trigger spec, so don't send it
    delete selectedTrigger.description;

    if (pipeline.parameterConfig && pipeline.parameterConfig.length) {
      selectedTrigger.parameters = values.parameters;
    }
    this.props.closeModal(command);
  };

  private pipelineChanged = (pipeline: IPipeline): void => {
    if (pipeline) {
      const executions: IExecution[] = this.props.application.executions.data || [];
      const currentPipelineExecutions = executions.filter(
        (execution) => execution.pipelineConfigId === pipeline.id && execution.isActive,
      );
      this.setState({
        currentPipelineExecutions,
        pipelineNotifications: pipeline.notifications || [],
      });
      this.parseManualExecution(pipeline);
    }
  };

  private triggerChanged = (trigger: ITrigger): void => {
    let triggerComponent = null;
    if (trigger && Registry.pipeline.hasManualExecutionComponentForTriggerType(trigger.type)) {
      triggerComponent = Registry.pipeline.getManualExecutionComponentForTriggerType(trigger.type);
    }
    this.setState({
      triggerComponent,
    });
  };

  private parseManualExecution = (pipeline: IPipeline): void => {
    if (pipeline.type === 'templatedPipeline' && (pipeline.stages === undefined || pipeline.stages.length === 0)) {
      PipelineTemplateReader.getPipelinePlan(pipeline as IPipelineTemplateConfig)
        .then((plan) => this.setStageComponentsForManualExecution(plan.stages))
        .catch(() => this.setStageComponentsForManualExecution(pipeline.stages));
    } else {
      this.setStageComponentsForManualExecution(pipeline.stages);
    }
  };

  private setStageComponentsForManualExecution = (stages: IStage[]): void => {
    const additionalComponents = stages.map((stage) => Registry.pipeline.getManualExecutionComponentForStage(stage));
    this.setState({ stageComponents: uniq(compact(additionalComponents)) });
  };

  private formatTriggers = (triggers: ITrigger[]): ITrigger[] => {
    return triggers.filter((t) => Registry.pipeline.hasManualExecutionComponentForTriggerType(t.type));
  };

  private formatPipeline = (pipeline: IPipeline) => {
    const newPipeline = clone(pipeline);
    // Inject the default value into the options list if it is absent
    newPipeline &&
      newPipeline.parameterConfig &&
      newPipeline.parameterConfig.forEach((parameterConfig) => {
        if (
          parameterConfig.default &&
          parameterConfig.options &&
          !parameterConfig.options.some((option) => option.value === parameterConfig.default)
        ) {
          parameterConfig.options.unshift({ value: parameterConfig.default });
        }
      });
    return newPipeline;
  };

  private formatParameterConfig = (parameters: IParameter[]): { [key: string]: any } => {
    const [, queryString] = window.location.href.split('?');
    const queryParams = UrlParser.parseQueryString(queryString);
    const result: { [key: string]: any } = {};
    parameters.forEach((parameter) => {
      const { name } = parameter;
      const { trigger } = this.props;
      const triggerParameters = trigger ? trigger.parameters : {};
      if (queryParams[name]) {
        result[name] = queryParams[name];
      } else {
        result[name] = triggerParameters[name] !== undefined ? triggerParameters[name] : parameter.default;
      }
    });
    return result;
  };

  private updateTriggerOptions = (triggers: ITrigger[]) => {
    triggers.map((t, i) => {
      observableFrom((Registry.pipeline.getManualExecutionComponentForTriggerType(t.type) as any).formatLabel(t))
        .pipe(takeUntil(this.destroy$))
        .subscribe(
          (label: string) => {
            const newTriggers = triggers.slice(0);
            newTriggers[i].description = label;
            this.setState({ triggers: newTriggers });
          },
          () => {
            this.setState({ triggers });
          },
        );
    });
  };

  private generateInitialValues = (pipeline: IPipeline): IPipelineCommand => {
    const user = AuthenticationService.getAuthenticatedUser();
    const userEmail = user.authenticated && user.name.includes('@') ? user.name : '';
    const triggers = this.formatTriggers(pipeline && pipeline.triggers ? pipeline.triggers : []);
    let trigger: ITrigger;
    if (this.props.trigger) {
      // Certain fields like correlationId will cause unexpected behavior if used to trigger
      // a different execution, others are just left unused. Let's exclude them.
      trigger = pickBy(this.props.trigger, (_, key) => !TRIGGER_FIELDS_TO_EXCLUDE.includes(key));

      if (trigger.type === 'manual' && triggers.length) {
        trigger.type = head(triggers).type;
      }
      // Find the pipeline.trigger that matches trigger (the trigger from the execution being re-run)
      const matchingTrigger = (pipeline.triggers || []).find((t) =>
        Object.keys(t)
          .filter((k) => k !== 'description')
          .every((k) => isEqual(get(t, k), get(trigger, k))),
      );
      // If we found a match, rehydrate it with everything from trigger, otherwise just default back to setting it to trigger
      trigger = matchingTrigger ? assign(matchingTrigger, trigger) : trigger;

      if (Registry.pipeline.hasManualExecutionComponentForTriggerType(trigger.type)) {
        // If the trigger has a manual component, we don't want to also explicitly
        // send along the artifacts from the last run, as the manual component will
        // populate enough information (ex: build number, docker tag) to re-inflate
        // these on a subsequent run.
        trigger.artifacts = [];
      }
    } else {
      trigger = head(triggers);
    }
    let parameters: { [key: string]: any } = {};
    if (pipeline && pipeline.parameterConfig) {
      parameters = this.formatParameterConfig(pipeline.parameterConfig);
    }
    return {
      pipeline: this.formatPipeline(pipeline),
      pipelineName: pipeline ? pipeline.name : '',
      dryRun: false,
      extraFields: {
        buildNumber: '',
      },
      notificationEnabled: false,
      notification: {
        type: 'email',
        address: userEmail,
        when: ['pipeline.complete', 'pipeline.failed'],
      },
      parameters,
      trigger,
      triggerInvalid: false,
    };
  };

  private validate = (values: IPipelineCommand): any => {
    const formValidator = new FormValidator(values);
    return formValidator.validateForm();
  };

  public render(): React.ReactElement<ManualExecutionModal> {
    const { dismissModal, pipeline } = this.props;
    const {
      applicationNotifications,
      currentPipelineExecutions,
      dryRunEnabled,
      modalHeader,
      pipelineNotifications,
      pipelineOptions,
      stageComponents,
      triggerComponent,
      triggers,
    } = this.state;
    const notifications = applicationNotifications.concat(pipelineNotifications);
    const pipelineCommand = this.generateInitialValues(pipeline);
    return (
      <SpinFormik<IPipelineCommand>
        ref={this.formikRef}
        initialValues={pipelineCommand}
        onSubmit={this.submit}
        validate={this.validate}
        render={(formik) => (
          <Form className={`form-horizontal`}>
            <ModalClose dismiss={dismissModal} />
            <Modal.Header>
              <Modal.Title>{modalHeader}</Modal.Title>
            </Modal.Header>
            <Modal.Body>
              <LayoutProvider value={ManualExecutionFieldLayout}>
                <div className="container-fluid modal-body-content">
                  {pipelineOptions.length > 0 && (
                    <PipelineOptions
                      formik={formik}
                      formatPipeline={this.formatPipeline}
                      formatTriggers={this.formatTriggers}
                      formatParameterConfig={this.formatParameterConfig}
                      pipelineOptions={pipelineOptions}
                      pipelineChanged={this.pipelineChanged}
                      triggerChanged={this.triggerChanged}
                      updateTriggerOptions={this.updateTriggerOptions}
                    />
                  )}
                  {formik.values.pipeline && (
                    <div className="form-group">
                      <div className={pipelineOptions.length > 0 ? 'col-md-6 col-md-offset-4' : 'col-md-10'}>
                        <p>
                          This will start a new run of <strong>{formik.values.pipeline.name}</strong>.
                        </p>
                      </div>
                    </div>
                  )}
                  {currentPipelineExecutions.length > 0 && (
                    <CurrentlyRunningExecutions currentlyRunningExecutions={currentPipelineExecutions} />
                  )}
                  {pipeline && pipeline.manualStartAlert && (
                    <Markdown
                      className={`alert alert-${
                        ['danger', 'warning', 'info'].includes(pipeline.manualStartAlert.type)
                          ? pipeline.manualStartAlert.type
                          : 'warning'
                      }`}
                      message={pipeline.manualStartAlert.message}
                    />
                  )}
                  {triggers && triggers.length > 0 && (
                    <Triggers
                      formik={formik}
                      triggers={triggers}
                      triggerChanged={this.triggerChanged}
                      triggerComponent={triggerComponent}
                    />
                  )}
                  {formik.values.pipeline &&
                    formik.values.pipeline.parameterConfig &&
                    formik.values.pipeline.parameterConfig.length > 0 && (
                      <Parameters formik={formik} parameters={formik.values.pipeline.parameterConfig} />
                    )}
                  {stageComponents.length > 0 && (
                    <StageManualComponents
                      command={formik.values}
                      components={stageComponents}
                      updateCommand={(path: string, value: any) => {
                        formik.setFieldValue(path, value);
                      }}
                    />
                  )}
                  {!isEmpty(get(formik.values, 'trigger.artifacts')) && (
                    <div className="form-group">
                      <label className="col-md-4 sm-label-right">Artifacts</label>
                      <div className="col-md-8">
                        <ArtifactList artifacts={formik.values.trigger.artifacts} />
                      </div>
                    </div>
                  )}
                  {dryRunEnabled && <DryRun />}
                  <NotificationDetails formik={formik} notifications={notifications} />
                </div>
              </LayoutProvider>
            </Modal.Body>
            <Modal.Footer>
              <button className="btn btn-default" onClick={dismissModal} type="button">
                Cancel
              </button>
              <SubmitButton isDisabled={!formik.isValid} isFormSubmit={true} submitting={false} label={'Run'} />
            </Modal.Footer>
          </Form>
        )}
      />
    );
  }
}
