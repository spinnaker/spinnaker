import { get } from 'lodash';
import { Duration } from 'luxon';
import React from 'react';

import { Application } from '../application/application.model';
import { IInstanceCounts, IStage, ITask, ITaskStep, ITimedItem } from '../domain';
import { Tooltip } from '../presentation';

export interface IPlatformHealthOverrideMessageProps {
  application: Application;
  step: ITaskStep;
  task: ITask;
}

export interface IPlatformHealthOverrideMessageState {
  showMessage: boolean;
}

export class PlatformHealthOverrideMessage extends React.Component<
  IPlatformHealthOverrideMessageProps,
  IPlatformHealthOverrideMessageState
> {
  private tooltipTemplate = (
    <span>
      <p>Task not completing?</p>
      <p>
        By default, Spinnaker does not consider cloud provider health (i.e. whether your instances have launched and are
        running) as a reliable indicator of instance health.
      </p>
      <p>
        If your instances do not provide a health indicator known to Spinnaker (e.g. a discovery service or load
        balancers), you should configure your application to consider only cloud provider health when executing tasks.
        This option is available under Application Attributes in the <b>Config tab</b>.
      </p>
    </span>
  );

  constructor(props: IPlatformHealthOverrideMessageProps) {
    super(props);

    let showMessage = false;
    const lastCapacity: IInstanceCounts = props.task.getValueFor('lastCapacityCheck');
    if (lastCapacity) {
      const lastCapacityTotal =
        lastCapacity.up +
        lastCapacity.down +
        lastCapacity.outOfService +
        lastCapacity.unknown +
        lastCapacity.succeeded +
        lastCapacity.failed;

      // Confirm that a). we're stuck on a clone or create task (not, e.g., an enable task)
      // and b). the step we're stuck on is within that clone or create task.
      const isRelevantTask: boolean = props.task.execution.stages.some((stage: IStage) => {
        return (
          (stage.type === 'cloneServerGroup' || stage.type === 'createServerGroup') &&
          stage.tasks.some((task: ITimedItem) => task.startTime === props.step.startTime)
        );
      });

      showMessage =
        isRelevantTask &&
        props.step.name === 'waitForUpInstances' &&
        props.step.runningTimeInMs > Duration.fromObject({ minutes: 5 }).as('milliseconds') &&
        lastCapacity.unknown > 0 &&
        lastCapacity.unknown === lastCapacityTotal &&
        !get(props.application, 'attributes.platformHealthOnly');
    }

    this.state = { showMessage };
  }

  public render() {
    if (!this.state.showMessage) {
      return null;
    }

    return (
      <Tooltip template={this.tooltipTemplate}>
        <i className="fa fa-exclamation-circle" style={{ fontSize: 'smaller' }} />
      </Tooltip>
    );
  }
}
