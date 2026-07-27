import React from 'react';

import type { IModalComponentProps, IServerGroup } from '@spinnaker/core';
import { ModalBody, ModalFooter, ModalHeader, Spinner, timestamp } from '@spinnaker/core';

import type { IEventDescription } from './serverGroupEventsReader.service';
import { ServerGroupEventsReader } from './serverGroupEventsReader.service';

export interface IEcsServerGroupEventsModalProps extends IModalComponentProps {
  serverGroup: IServerGroup;
}

interface IEcsServerGroupEventsModalState {
  loading: boolean;
  error: boolean;
  events: IEventDescription[];
}

export class EcsServerGroupEventsModal extends React.Component<
  IEcsServerGroupEventsModalProps,
  IEcsServerGroupEventsModalState
> {
  public state: IEcsServerGroupEventsModalState = { loading: true, error: false, events: [] };

  private mounted = false;
  private requestId = 0;

  public componentDidMount(): void {
    this.mounted = true;
    this.loadEvents();
  }

  public componentDidUpdate(previousProps: IEcsServerGroupEventsModalProps): void {
    if (previousProps.serverGroup !== this.props.serverGroup) {
      this.loadEvents();
    }
  }

  public componentWillUnmount(): void {
    this.mounted = false;
    this.requestId++;
  }

  private loadEvents(): void {
    const requestId = ++this.requestId;
    this.setState({ loading: true, error: false, events: [] });
    ServerGroupEventsReader.getEvents(this.props.serverGroup).then(
      (events) => {
        if (this.mounted && requestId === this.requestId) {
          this.setState({ loading: false, error: false, events });
        }
      },
      () => {
        if (this.mounted && requestId === this.requestId) {
          this.setState({ loading: false, error: true, events: [] });
        }
      },
    );
  }

  public render() {
    const { dismissModal, serverGroup } = this.props;
    const { loading, error, events } = this.state;
    return (
      <>
        <ModalHeader>{`Server Group Events for ${serverGroup.name}`}</ModalHeader>
        <ModalBody>
          {loading && (
            <div className="horizontal center middle sp-margin-xl-yaxis">
              <Spinner size="small" />
            </div>
          )}
          {!loading && error && (
            <div className="text-center">
              <p>{`There was an error loading events for ${serverGroup.name}. Please try again later.`}</p>
            </div>
          )}
          {!loading && !error && events.length === 0 && (
            <div className="text-center">
              <p>{`No ECS events found for ${serverGroup.name}.`}</p>
            </div>
          )}
          {!loading &&
            !error &&
            events.map((event, index) => (
              <div key={event.id || index}>
                <p className="clearfix">
                  <span
                    className={`label label-${
                      event.status === 'Success' ? 'success' : event.status === 'Transition' ? 'info' : 'danger'
                    } pull-left`}
                  >
                    {event.status}
                  </span>
                  <span className="label label-default pull-right">{timestamp(event.createdAt)}</span>
                </p>
                <div>{event.message}</div>
                {index < events.length - 1 && <hr />}
              </div>
            ))}
        </ModalBody>
        <ModalFooter
          primaryActions={
            <button className="btn btn-primary" onClick={dismissModal}>
              Close
            </button>
          }
        />
      </>
    );
  }
}
