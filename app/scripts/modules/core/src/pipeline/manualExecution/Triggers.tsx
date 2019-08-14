import * as React from 'react';
import { Observable, Subject } from 'rxjs';
import Select, { Option } from 'react-select';
import { clone, head } from 'lodash';
import { FormikProps } from 'formik';

import { IPipelineCommand, ITrigger } from 'core/domain';
import { FormField } from 'core/presentation';
import { Registry } from 'core/registry';

import { ITriggerTemplateComponentProps, TriggerTemplate } from './TriggerTemplate';

export interface ITriggersProps {
  triggerChanged: (t: ITrigger) => void;
  triggerComponent?: React.ComponentType<ITriggerTemplateComponentProps>;
  triggers: ITrigger[];
  formik: FormikProps<IPipelineCommand>;
}

export class Triggers extends React.Component<ITriggersProps> {
  private destroy$ = new Subject();

  public componentDidMount() {
    this.updateTriggerDescription(this.props.formik.values.trigger);
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  private updateCommand = (path: string, value: any) => {
    const { formik } = this.props;
    formik.setFieldValue(path, value);
  };

  private updateTriggerDescription = (trigger: ITrigger) => {
    if (trigger && !trigger.description) {
      Observable.fromPromise(
        (Registry.pipeline.getManualExecutionComponentForTriggerType(trigger.type) as any).formatLabel(trigger),
      )
        .takeUntil(this.destroy$)
        .subscribe((label: string) => {
          const newTrigger = clone(trigger);
          newTrigger.description = label;
          this.props.formik.setFieldValue('trigger', newTrigger);
          this.props.triggerChanged(trigger);
        });
    } else {
      this.props.triggerChanged(trigger);
    }
  };

  private triggerSelected = (option: Option<string>) => {
    const triggerDescription = option.value;
    const { formik, triggers, triggerChanged } = this.props;
    const trigger = triggers.find(t => t.description === triggerDescription);
    formik.setFieldValue('trigger', trigger);
    formik.setFieldValue('triggerInvalid', false);
    triggerChanged(trigger);
  };

  public render() {
    const { formik, triggerComponent, triggers } = this.props;
    return (
      <div className="form-group row">
        <div className={'form-group'}>
          <label className={'col-md-4 sm-label-right'}>Trigger</label>
          <div className="col-md-6">
            {triggers.length > 1 && (
              <FormField
                onChange={this.triggerSelected}
                value={formik.values.trigger ? formik.values.trigger.description : ''}
                input={props => (
                  <Select
                    {...props}
                    className={'trigger-select'}
                    clearable={false}
                    options={triggers.map(t => ({
                      label: t.description,
                      value: t.description,
                    }))}
                  />
                )}
              />
            )}
            {triggers.length === 1 && <p className="form-control-static">{head(triggers).description}</p>}
          </div>
        </div>

        {triggerComponent && (
          <div className={'trigger-template'}>
            <TriggerTemplate
              key={formik.values.trigger.description}
              updateCommand={this.updateCommand}
              component={triggerComponent}
              command={formik.values}
            />
          </div>
        )}
      </div>
    );
  }
}
