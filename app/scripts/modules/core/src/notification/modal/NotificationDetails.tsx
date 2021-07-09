import { FormikProps } from 'formik';
import React from 'react';
import { Option } from 'react-select';

import { WhenChecklistInput } from './WhenChecklistInput';
import { INotification, INotificationTypeConfig } from '../../domain';
import { NotificationTransformer } from '../notification.transformer';
import { FormikFormField, TextAreaInput } from '../../presentation';
import { Registry } from '../../registry';
import { NotificationSelector } from '../selector/NotificationSelector';
import { MANUAL_JUDGEMENT_WHEN_OPTIONS, PIPELINE_WHEN_OPTIONS, STAGE_WHEN_OPTIONS } from './whenOptions';

import './editNotification.less';

export interface INotificationDetailsProps {
  formik: FormikProps<INotification>;
  level?: string;
  stageType?: string;
}

export interface INotificationDetailsState {
  notificationTypes: Option[];
  whenOptions: string[];
}

export class NotificationDetails extends React.Component<INotificationDetailsProps, INotificationDetailsState> {
  constructor(props: INotificationDetailsProps) {
    super(props);

    let whenOptions = [];
    if (props.level === 'application' || props.level === 'pipeline') {
      whenOptions = PIPELINE_WHEN_OPTIONS;
    } else if (props.stageType === 'manualJudgment') {
      whenOptions = MANUAL_JUDGEMENT_WHEN_OPTIONS;
    } else {
      whenOptions = STAGE_WHEN_OPTIONS;
    }

    this.state = {
      notificationTypes: Registry.pipeline.getNotificationTypes().map((type: INotificationTypeConfig) => ({
        label: type.label,
        value: type.key,
      })),
      whenOptions,
    };
  }

  public componentDidMount() {
    const { formik } = this.props;
    if (!formik.values.type) {
      const { notificationTypes } = this.state;
      formik.setFieldValue('type', notificationTypes && notificationTypes[0] ? notificationTypes[0].value : '');
    }
  }

  private renderCustomMessage = (type: string, whenOption: string): React.ReactNode => {
    if (whenOption !== 'manualJudgment' && ['email', 'slack', 'googlechat', 'microsoftteams'].includes(type)) {
      return (
        <FormikFormField
          name={`message["${whenOption}"].text`}
          input={(props) => <TextAreaInput {...props} placeholder="enter a custom notification message (optional)" />}
        />
      );
    } else {
      return null;
    }
  };

  private onNotificationTypeChange = (type: string) => {
    const notificationTypeUpdate = Registry.pipeline.getNotificationConfig(type);
    if (!!notificationTypeUpdate) {
      this.props.formik.setFieldValue('address', '');
    }
    this.props.formik.setFieldValue('when', [...this.props.formik.values.when]);
  };

  public render() {
    const { onNotificationTypeChange, renderCustomMessage } = this;
    const { formik, level, stageType } = this.props;
    const { values } = formik;
    const { whenOptions } = this.state;
    return (
      <>
        <div className={'notification-details'}>
          <NotificationSelector onNotificationTypeChange={onNotificationTypeChange} type={values.type} />
          {(stageType || level) && (
            <div className="sp-margin-m-bottom">
              <div className={'form-group'}>
                <label className={'col-md-4 sm-label-right'}>Notify when</label>
                <div className="col-md-6">
                  <FormikFormField
                    name="when"
                    input={(props) => (
                      <WhenChecklistInput
                        {...props}
                        options={whenOptions.map((o) => ({
                          value: o,
                          label: NotificationTransformer.getNotificationWhenDisplayName(o, level, stageType),
                          additionalFields: renderCustomMessage(values.type, o),
                        }))}
                      />
                    )}
                    required={true}
                  />
                </div>
              </div>
            </div>
          )}
        </div>
      </>
    );
  }
}
