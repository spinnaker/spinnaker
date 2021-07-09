import { filter } from 'lodash';
import React from 'react';

import { QuietPeriodBadge } from './QuietPeriodBadge';
import { SETTINGS } from '../../config';
import { IPipeline } from '../../domain/IPipeline';

export interface ITriggersTagProps {
  pipeline: IPipeline;
}

export interface ITriggersTagState {
  triggerCount: number;
  activeTriggerCount: number;
}

export class TriggersTag extends React.Component<ITriggersTagProps, ITriggersTagState> {
  private quietPeriodStart: Date;
  private quietPeriodEnd: Date;

  constructor(props: ITriggersTagProps) {
    super(props);

    let triggerCount = 0;
    let activeTriggerCount = 0;
    let quietPeriodEnabled = false;

    const pipeline = this.props.pipeline;
    if (pipeline && pipeline.triggers && pipeline.triggers.length) {
      triggerCount = pipeline.triggers.length;
      activeTriggerCount = filter(pipeline.triggers, { enabled: true }).length;
      quietPeriodEnabled = Boolean(pipeline.respectQuietPeriod);
    }

    const hasQuietPeriod = SETTINGS.feature.quietPeriod && SETTINGS.quietPeriod && SETTINGS.quietPeriod.length === 2;

    if (hasQuietPeriod && quietPeriodEnabled) {
      this.quietPeriodStart = new Date(SETTINGS.quietPeriod[0]);
      this.quietPeriodEnd = new Date(SETTINGS.quietPeriod[1]);
    }

    this.state = {
      triggerCount,
      activeTriggerCount,
    };
  }

  public render(): React.ReactElement<TriggersTag> {
    const { pipeline } = this.props;
    const { triggerCount, activeTriggerCount } = this.state;

    if (triggerCount > 0) {
      const now = new Date();
      const inQuietPeriod = this.quietPeriodStart < now && now < this.quietPeriodEnd;

      const triggers =
        triggerCount === 1
          ? 'Trigger'
          : activeTriggerCount === triggerCount || inQuietPeriod
          ? 'All triggers'
          : 'Some triggers';
      const displayTriggers = `${triggers}: ${activeTriggerCount === 0 || inQuietPeriod ? 'disabled' : 'enabled'}`;

      return (
        <div
          className={`triggers-toggle ${activeTriggerCount ? '' : 'disabled'}`}
          style={{ visibility: pipeline.disabled ? 'hidden' : 'visible' }}
        >
          <span>
            <span>
              <QuietPeriodBadge start={this.quietPeriodStart} end={this.quietPeriodEnd} /> {displayTriggers}
            </span>
          </span>
        </div>
      );
    }
    return <div />;
  }
}
