import { get } from 'lodash';
import React from 'react';
import { Button, Modal } from 'react-bootstrap';

import { IPageButtonProps } from './PageButton';
import { Application, ApplicationModelBuilder } from '../application';
import { SETTINGS } from '../config';
import { SubmitButton } from '../modal';
import { IPagerDutyService } from './pagerDuty.read.service';
import { PagerDutyWriter } from './pagerDuty.write.service';
import { ReactInjector } from '../reactShims';
import { TaskMonitor, TaskMonitorWrapper } from '../task';

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
    const {
      $uiRouter: {
        globals: {
          params: { subject, details },
        },
      },
    } = ReactInjector;
    const defaultSubject = subject || get(SETTINGS, 'pagerDuty.defaultSubject', 'Urgent Issue');
    const defaultDetails = details || get(SETTINGS, 'pagerDuty.defaultDetails', '');

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
    ReactInjector.$state.go('.', { subject: value }, { location: 'replace' });
    this.setState({ subject: value });
  };

  private handleDetailsChanged = (event: any): void => {
    const value = event.target.value;
    ReactInjector.$state.go('.', { details: value }, { location: 'replace' });
    this.setState({ details: value });
  };

  public sendPage = (): void => {
    const { applications, services } = this.props;
    const defaultApp = ApplicationModelBuilder.createStandaloneApplication('spinnaker');
    const ownerApp = applications && applications.length === 1 ? applications[0] : defaultApp;

    const taskMonitor = new TaskMonitor({
      application: ownerApp,
      title: `Sending page to ${this.state.pageCount} policies`,
      modalInstance: TaskMonitor.modalInstanceEmulation(() => this.close()),
    });

    const submitMethod = () => {
      const { subject, details } = this.state;

      return PagerDutyWriter.sendPage(
        applications,
        services.map((s) => s.integration_key),
        subject,
        ownerApp,
        {
          details,
        },
      );
    };

    taskMonitor.submit(submitMethod);

    this.setState({ taskMonitor, submitting: true });
  };

  public render() {
    const formValid = true;
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
                  {services.map((service) => (
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
