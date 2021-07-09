import { module } from 'angular';
import { isEqual } from 'lodash';
import prettyMilliseconds from 'pretty-ms';
import React from 'react';
import { react2angular } from 'react2angular';

import { HelpField, IServerGroupDetailsSectionProps, withErrorBoundary } from '@spinnaker/core';

import { EditDisruptionBudgetModal } from './EditDisruptionBudgetModal';
import {
  getDefaultJobDisruptionBudgetForApp,
  ITitusServerGroupCommand,
} from '../../configure/serverGroupConfiguration.service';
import {
  DisruptionBudgetDescription,
  IFieldOption,
} from '../../configure/wizard/pages/disruptionBudget/JobDisruptionBudget';
import { policyOptions } from '../../configure/wizard/pages/disruptionBudget/PolicyOptions';
import { rateOptions } from '../../configure/wizard/pages/disruptionBudget/RateOptions';
import { IJobDisruptionBudget, ITitusServerGroup } from '../../../domain';
import { TitusReactInjector } from '../../../reactShims';

interface IDisruptionBudgetSection extends IServerGroupDetailsSectionProps {
  serverGroup: ITitusServerGroup;
}

export class DisruptionBudgetSection extends React.Component<IDisruptionBudgetSection> {
  private SectionHeading = ({
    budget,
    options,
    label,
  }: {
    budget: IJobDisruptionBudget;
    options: IFieldOption[];
    label: string;
  }): JSX.Element => {
    const selected = options.find((o) => !!budget[o.field]);
    if (!selected) {
      return null;
    }
    return (
      <div>
        <div className="bold">{label}</div>
        {selected.label} <HelpField content={selected.description} />
      </div>
    );
  };

  private Policy = ({ budget }: { budget: IJobDisruptionBudget }): JSX.Element => {
    const { ParentheticalDuration } = this;
    if (budget.availabilityPercentageLimit) {
      return (
        <div>
          <div className="bold">Percentage of Healthy Containers</div>
          {budget.availabilityPercentageLimit.percentageOfHealthyContainers} percent
        </div>
      );
    }
    if (budget.relocationLimit) {
      return (
        <div>
          <div className="bold">Limit</div>
          {budget.relocationLimit.limit} task{budget.relocationLimit.limit !== 1 && 's'}
        </div>
      );
    }
    if (budget.unhealthyTasksLimit) {
      return (
        <div>
          <div className="bold">Limit of Unhealthy Containers</div>
          {budget.unhealthyTasksLimit.limitOfUnhealthyContainers} container
          {budget.unhealthyTasksLimit.limitOfUnhealthyContainers !== 1 && 's'}
        </div>
      );
    }
    if (budget.selfManaged) {
      return (
        <div>
          <div className="bold">Relocation Time</div>
          {budget.selfManaged.relocationTimeMs > 0 && (
            <ParentheticalDuration durationMs={budget.selfManaged.relocationTimeMs} />
          )}
          {budget.selfManaged.relocationTimeMs === 0 && '0 ms (immediate)'}
        </div>
      );
    }
    return null;
  };

  // it's bad enough that we make users enter these values as milliseconds; let's not make them translate it
  private ParentheticalDuration = ({ durationMs }: { durationMs: number }) => (
    <span>
      {durationMs} ms
      {durationMs > 1000 && ` (${prettyMilliseconds(durationMs)})`}
    </span>
  );

  private Rate = ({ budget }: { budget: IJobDisruptionBudget }): JSX.Element => {
    const { ParentheticalDuration } = this;
    if (budget.ratePerInterval) {
      return (
        <>
          <div>
            <div className="bold">Interval</div>
            <ParentheticalDuration durationMs={budget.ratePerInterval.intervalMs} />
          </div>
          <div>
            <div className="bold">Limit</div>
            {budget.ratePerInterval.limitPerInterval} task{budget.ratePerInterval.limitPerInterval !== 1 && 's'}
          </div>
        </>
      );
    }
    if (budget.ratePercentagePerInterval) {
      return (
        <>
          <div>
            <div className="bold">Interval</div>
            <ParentheticalDuration durationMs={budget.ratePercentagePerInterval.intervalMs} />
          </div>
          <div>
            <div className="bold">Percentage per Interval</div>
            {budget.ratePercentagePerInterval.percentageLimitPerInterval} percent
          </div>
        </>
      );
    }
    return null;
  };

  // given a collection of days, e.g. ['Monday', 'Tuesday', 'Wednesday', 'Saturday'], return 'Mon-Wed, Sat'
  private groupedDays = (days: string[]): string => {
    const allDays = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
    const sortedDays = days.sort((d1, d2) => allDays.indexOf(d1) - allDays.indexOf(d2));
    const groups: string[] = [];
    const currentGroup: string[] = [];
    allDays.slice(allDays.indexOf(sortedDays[0])).forEach((d) => {
      if (sortedDays.includes(d)) {
        currentGroup.push(d);
      } else {
        if (currentGroup.length) {
          groups.push(this.toDayRangeString(currentGroup));
          currentGroup.length = 0;
        }
      }
    });
    if (currentGroup.length) {
      groups.push(this.toDayRangeString(currentGroup));
    }
    return groups.join(', ');
  };

  private toDayRangeString(days: string[]) {
    if (!days.length) {
      return null;
    }
    if (days.length === 1) {
      return days[0].substr(0, 3);
    }
    return `${days[0].substr(0, 3)}-${days[days.length - 1].substr(0, 3)}`;
  }

  private TimeWindows = ({ budget }: { budget: IJobDisruptionBudget }): JSX.Element => {
    const { groupedDays } = this;
    const hasWindows = budget.timeWindows && budget.timeWindows.length > 0;
    return (
      <>
        <div className="bold">When can disruption occur?</div>
        <div>
          {hasWindows &&
            budget.timeWindows.map((tw1, i1) => {
              return tw1.hourlyTimeWindows.map((tw2, i2) => (
                <div key={`${i1}.${i2}`}>
                  {groupedDays(tw1.days)}, {tw2.startHour}:00 - {tw2.endHour}:00 {tw1.timeZone}
                </div>
              ));
            })}
          {!hasWindows && 'Any time'}
        </div>
      </>
    );
  };

  private editBudget = (): void => {
    const { app, serverGroup } = this.props;
    TitusReactInjector.titusServerGroupCommandBuilder
      .buildServerGroupCommandFromExisting(app, serverGroup)
      .then((command: ITitusServerGroupCommand) => {
        EditDisruptionBudgetModal.show({ command, application: app, serverGroup });
      });
  };

  public render() {
    const { Policy, SectionHeading, Rate, TimeWindows } = this;
    const serverGroup: ITitusServerGroup = this.props.serverGroup;
    const hasDefaultMigrationPolicy =
      !serverGroup.migrationPolicy || serverGroup.migrationPolicy.type === 'SystemDefault';
    const defaultBudget = getDefaultJobDisruptionBudgetForApp(this.props.app);
    const budget = serverGroup.disruptionBudget || defaultBudget;
    const usingDefault = !hasDefaultMigrationPolicy && isEqual(budget, defaultBudget);
    return (
      <>
        <DisruptionBudgetDescription />
        {usingDefault && <div>(default policy)</div>}
        <div className="bottom-border">
          <SectionHeading budget={budget} options={policyOptions} label="Policy" />
          <Policy budget={budget} />
        </div>
        <div className="bottom-border">
          <SectionHeading budget={budget} options={rateOptions} label="Rate" />
          <Rate budget={budget} />
        </div>
        <TimeWindows budget={budget} />
        <div className="sp-margin-l-top">
          <a className="clickable" onClick={this.editBudget}>
            Edit Disruption Budget
          </a>
        </div>
      </>
    );
  }
}

export const DISRUPTION_BUDGET_DETAILS_SECTION = 'spinnaker.titus.disruptionbudget.section';

module(DISRUPTION_BUDGET_DETAILS_SECTION, []).component(
  'titusDisruptionBudgetSection',
  react2angular(withErrorBoundary(DisruptionBudgetSection, 'titusDisruptionBudgetSection'), ['serverGroup', 'app']),
);
