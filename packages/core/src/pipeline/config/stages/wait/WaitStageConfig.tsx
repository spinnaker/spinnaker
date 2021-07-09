import React from 'react';

import { IStageConfigProps } from '../common';
import { StageConfigField } from '../common/stageConfigField/StageConfigField';
import { IStage } from '../../../../domain';
import { SpelNumberInput } from '../../../../widgets';

export interface IWaitStageConfigState {
  enableCustomSkipWaitText: boolean;
  waitTime: number | string;
  skipWaitText: string;
}

export const DEFAULT_SKIP_WAIT_TEXT = 'The pipeline will proceed immediately, marking this stage completed.';

export class WaitStageConfig extends React.Component<IStageConfigProps, IWaitStageConfigState> {
  public static getDerivedStateFromProps(
    props: IStageConfigProps,
    state: IWaitStageConfigState,
  ): IWaitStageConfigState {
    const { stage } = props;
    const { waitTime } = stage;
    if (waitTime === undefined || (!Number.isNaN(waitTime) && waitTime < 0)) {
      stage.waitTime = 30;
    }
    return {
      enableCustomSkipWaitText: !!stage.skipWaitText || state.enableCustomSkipWaitText,
      waitTime: stage.waitTime,
      skipWaitText: stage.skipWaitText,
    };
  }

  constructor(props: IStageConfigProps) {
    super(props);
    this.state = this.getState(props.stage);
  }

  private getState(stage: IStage): IWaitStageConfigState {
    const { waitTime } = stage;
    if (waitTime === undefined || (!Number.isNaN(waitTime) && waitTime < 0)) {
      stage.waitTime = 30;
    }
    return {
      enableCustomSkipWaitText: !!stage.skipWaitText,
      waitTime: stage.waitTime,
      skipWaitText: stage.skipWaitText,
    };
  }

  private updateWaitTime = (waitTime: number | string) => {
    this.props.stage.waitTime = waitTime;
    this.setState({ waitTime });
    this.props.stageFieldUpdated();
  };

  private toggleCustomSkipWaitText = (event: React.ChangeEvent<HTMLInputElement>): void => {
    this.props.stage.skipWaitText = undefined;
    if (!event.target.checked) {
      this.props.stageFieldUpdated();
    }
    this.setState({ enableCustomSkipWaitText: event.target.checked, skipWaitText: undefined });
  };

  private customSkipWaitTextChanged = (event: React.ChangeEvent<HTMLTextAreaElement>): void => {
    const skipWaitText = event.target.value || undefined;
    this.props.stage.skipWaitText = skipWaitText;
    this.setState({ skipWaitText });
    this.props.stageFieldUpdated();
  };

  public render() {
    const { waitTime, enableCustomSkipWaitText, skipWaitText } = this.state;
    return (
      <div className="form-horizontal">
        <StageConfigField label="Wait time (seconds)" fieldColumns={6}>
          <div>
            <SpelNumberInput value={waitTime} min={0} onChange={this.updateWaitTime} />
          </div>
        </StageConfigField>
        <div className="form-group">
          <div className="col-md-8 col-md-offset-3">
            <div className="checkbox">
              <label>
                <input type="checkbox" onChange={this.toggleCustomSkipWaitText} checked={enableCustomSkipWaitText} />
                <span> Show custom warning when users skip wait</span>
              </label>
            </div>
          </div>
          {enableCustomSkipWaitText && (
            <div className="col-md-8 col-md-offset-3 checkbox-padding">
              <textarea
                className="form-control"
                rows={4}
                placeholder={`Default text: '${DEFAULT_SKIP_WAIT_TEXT}' (HTML is okay)`}
                style={{ marginTop: '5px' }}
                value={skipWaitText}
                onChange={this.customSkipWaitTextChanged}
              />
            </div>
          )}
        </div>
      </div>
    );
  }
}
