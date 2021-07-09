import React from 'react';

import { IExecutionStageSummary } from '../../../../domain';
import { HoverablePopover } from '../../../../presentation/HoverablePopover';

import './groupStage.less';

export interface IGroupExecutionPopoverProps {
  stage: IExecutionStageSummary;
  width: number;
  subStageClicked?: (groupStage: IExecutionStageSummary, stage: IExecutionStageSummary) => void;
  svgMode?: boolean;
}

export interface IGroupedStageListItemProps {
  stage: IExecutionStageSummary;
  stageClicked?: (stage: IExecutionStageSummary) => void;
}

class GroupedStageListItem extends React.Component<IGroupedStageListItemProps> {
  private onClick = (): void => {
    if (this.props.stageClicked) {
      this.props.stageClicked(this.props.stage);
    }
  };

  public render(): React.ReactElement<GroupedStageListItem> {
    const { stage } = this.props;
    const markerClassName = [
      'clickable',
      'stage-status',
      'execution-marker',
      `stage-type-${stage.type.toLowerCase()}`,
      `execution-marker-${stage.status.toLowerCase()}`,
      stage.isRunning ? 'glowing' : '',
    ].join(' ');

    return (
      <a onClick={this.onClick} className="clickable">
        <li>
          <div>
            <div className={markerClassName} />
            <span className="stage-name">{stage.name}</span>
          </div>
        </li>
      </a>
    );
  }
}

export class GroupExecutionPopover extends React.Component<IGroupExecutionPopoverProps> {
  private subStageClicked = (subStage: IExecutionStageSummary): void => {
    if (this.props.subStageClicked) {
      this.props.subStageClicked(this.props.stage, subStage);
    }
  };

  public render() {
    const { stage, width, svgMode } = this.props;

    const template = (
      <div style={{ minWidth: width - 30 + 'px' }}>
        <div className="group-name">{stage.name.toUpperCase()}</div>
        <ul className="group-execution-label-list">
          {stage.groupStages.map((s) => (
            <GroupedStageListItem key={s.name} stage={s} stageClicked={this.subStageClicked} />
          ))}
        </ul>
      </div>
    );

    return (
      <HoverablePopover
        className="group-stages-list-popover"
        svgMode={svgMode}
        delayHide={50}
        placement="bottom"
        template={template}
      >
        {this.props.children}
      </HoverablePopover>
    );
  }
}
