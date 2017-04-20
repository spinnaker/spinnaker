import * as React from 'react';
import { filter } from 'lodash';

import { IPipeline } from 'core/domain/IPipeline';

interface IProps {
  pipeline: IPipeline;
};

interface IState {
  triggerCount: number;
  activeTriggerCount: number;
};

export class TriggersTag extends React.Component<IProps, IState> {
  constructor(props: IProps) {
    super(props);

    let triggerCount = 0,
        activeTriggerCount = 0;

    const pipeline = this.props.pipeline;
    if (pipeline && pipeline.triggers && pipeline.triggers.length) {
      triggerCount = pipeline.triggers.length;
      activeTriggerCount = filter(pipeline.triggers, { enabled: true }).length;
    }

    this.state = {
      triggerCount,
      activeTriggerCount,
    }
  }

  public render(): React.ReactElement<TriggersTag> {
    const triggerCount = this.state.triggerCount,
          activeTriggerCount = this.state.activeTriggerCount;

    if (triggerCount > 0) {
      const triggers = triggerCount === 1 ? 'Trigger' : activeTriggerCount === triggerCount ? 'All triggers' : 'Some triggers';
      const displayTriggers = `${triggers}: ${activeTriggerCount === 0 ? 'disabled' : 'enabled'}`;
      return (
        <div className={`triggers-toggle ${activeTriggerCount ? '' : 'disabled'}`} style={{visibility: this.props.pipeline.disabled ? 'hidden' : 'visible'}}>
          <span>
            <span>
              { displayTriggers }
            </span>
          </span>
        </div>
      );
    }
    return <div/>;
  }
}
