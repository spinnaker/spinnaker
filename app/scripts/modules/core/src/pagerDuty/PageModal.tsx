import * as React from 'react';
import { Button, Modal } from 'react-bootstrap';
import { get } from 'lodash';

import { Application, ApplicationModelBuilder } from 'core/application';
import { IPagerDutyService, PagerDutyWriter } from 'core/pagerDuty';
import { NgReact } from 'core/reactShims';
import { SETTINGS } from 'core/config';
import { SubmitButton } from 'core/modal';
import { TaskMonitor } from 'core/task';

import { IPageButtonProps } from './PageButton';

export interface IPageModalProps {
  applications?: Application[];
  services: IPagerDutyService[];
  show?: boolean;
  closeCallback: (completed: boolean) => void;
}

export interface IPageModalState {
  accountName: string;
  details: string;
  pageCount: number;
  subject: string;
  submitting: boolean;
  taskMonitor: TaskMonitor;
}

export class PageModal extends React.Component<IPageModalProps, IPageModalState> {
  constructor(props: IPageModalProps) {
    super(props);
    this.state = this.getDefaultState(props);
  }

  public componentWillReceiveProps(nextProps: IPageButtonProps): void {
    if (nextProps.services.length !== this.state.pageCount) {
      this.setState({ pageCount: nextProps.services.length });
    }
  }

  private getDefaultState(props: IPageModalProps): IPageModalState {
    const defaultSubject = get(SETTINGS, 'pagerDuty.defaultSubject', 'Urgent Issue');
    const defaultDetails = get(SETTINGS, 'pagerDuty.defaultDetails', '');

    return {
      accountName: (SETTINGS.pagerDuty && SETTINGS.pagerDuty.accountName) || '',
      subject: defaultSubject,
      details: defaultDetails,
      pageCount: props.services.length,
      submitting: false,
      taskMonitor: null,
    };
  }

  public close = (evt?: React.MouseEvent<any>): void => {
    evt && evt.stopPropagation();
    this.setState(this.getDefaultState(this.props));
    this.props.closeCallback(false);
  };

  private handleSubjectChanged = (event: any): void => {
    const value = event.target.value;
    this.setState({ subject: value });
  };

  private handleDetailsChanged = (event: any): void => {
    const value = event.target.value;
    this.setState({ details: value });
  };

  public sendPage = (): void => {
    const taskMonitor = new TaskMonitor({
      application: new ApplicationModelBuilder().createStandaloneApplication('spinnaker'),
      title: `Sending page to ${this.state.pageCount} policies`,
      modalInstance: TaskMonitor.modalInstanceEmulation(() => this.close()),
      onTaskComplete: () => this.props.closeCallback(true),
    });

    const submitMethod = () => {
      const { applications, services } = this.props;
      const { subject, details } = this.state;

      return PagerDutyWriter.sendPage(applications, services.map(s => s.integration_key), subject, { details });
    };

    taskMonitor.submit(submitMethod);

    this.setState({ taskMonitor, submitting: true });
  };

  public render() {
    const formValid = true;

    const { TaskMonitorWrapper } = NgReact;
    const { services } = this.props;
    const { accountName, details, pageCount, subject, submitting, taskMonitor } = this.state;

    return (
      <Modal show={this.props.show} onHide={this.close} className="page-modal" backdrop="static">
        <TaskMonitorWrapper monitor={taskMonitor} />
        {!submitting && (
          <Modal.Header closeButton={true}>
            <Modal.Title>
              Page {pageCount} {pageCount === 1 ? 'service' : 'services'}
            </Modal.Title>
          </Modal.Header>
        )}
        {!submitting && (
          <Modal.Body>
            <div>
              <div>
                <label>Services to Page</label>
                <div>
                  {services.map(service => (
                    <span className="service-to-page" key={service.integration_key}>
                      <a
                        title={service.name}
                        href={`https://${accountName}.pagerduty.com/services/${service.id}`}
                        target="_blank"
                      >
                        {service.name}
                      </a>
                    </span>
                  ))}
                </div>
              </div>
              <div>
                <label>Subject</label>
                <input
                  name="subject"
                  type="text"
                  style={{ width: '100%' }}
                  value={subject}
                  onChange={this.handleSubjectChanged}
                />
              </div>
              <div>
                <label>Details (Not visible/heard in phone/SMS)</label>
                <textarea
                  name="details"
                  style={{ width: '100%', height: '260px' }}
                  value={details}
                  onChange={this.handleDetailsChanged}
                />
              </div>
            </div>
          </Modal.Body>
        )}
        {!submitting && (
          <Modal.Footer>
            <Button onClick={this.close}>Cancel</Button>
            <SubmitButton
              label="Send Page"
              submitting={submitting}
              isDisabled={!formValid || submitting}
              onClick={this.sendPage}
            />
          </Modal.Footer>
        )}
      </Modal>
    );
  }
}
