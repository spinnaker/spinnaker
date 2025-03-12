import * as React from 'react';

import { QuietPeriodBadge } from './QuietPeriodBadge';
import type { ITrigger } from '../../domain';
import type { IPipeline } from '../../domain/IPipeline';
import { useQuietPeriod } from './useQuietPeriod.hook';

export interface ITriggersTagProps {
  pipeline: IPipeline;
}

function getTriggerLabel(totalTriggers: number, activeTriggers: number) {
  if (totalTriggers === 1) {
    return `Trigger: ${activeTriggers ? 'enabled' : 'disabled'}`;
  } else if (totalTriggers === activeTriggers) {
    return `All triggers: enabled`;
  } else if (activeTriggers === 0) {
    return `All triggers: disabled`;
  } else {
    return `Some triggers: enabled`;
  }
}

export function TriggersTag(props: ITriggersTagProps) {
  const quietPeriod = useQuietPeriod();

  const { pipeline } = props;
  const triggers = pipeline?.triggers ?? [];
  const isTriggerDisabled = (t: ITrigger) =>
    !t.enabled ||
    (pipeline.respectQuietPeriod && quietPeriod.currentStatus === 'DURING_QUIET_PERIOD' && t.type !== 'pipeline');
  const activeTriggers = triggers.filter((t: ITrigger) => !isTriggerDisabled(t));

  if (triggers.length === 0) {
    return <div />;
  }

  return (
    <div
      className={`triggers-toggle ${activeTriggers.length > 0 ? '' : 'disabled'}`}
      style={{ visibility: pipeline.disabled ? 'hidden' : 'visible' }}
    >
      <span className="flex-container-h margin-between-sm baseline">
        {pipeline.respectQuietPeriod && <QuietPeriodBadge />}
        <span>{getTriggerLabel(triggers.length, activeTriggers.length)}</span>
      </span>
    </div>
  );
}
