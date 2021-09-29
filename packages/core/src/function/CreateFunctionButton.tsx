import React from 'react';

import type { Application } from '../application';
import { CloudProviderRegistry, ProviderSelectionService } from '../cloudProvider';
import type { IFunction } from '../domain';
import type { IFunctionUpsertCommand } from './function.write.service';
import type { IModalComponentProps } from '../presentation';
import { Tooltip } from '../presentation';

export interface IFunctionModalProps extends IModalComponentProps {
  className?: string;
  dialogClassName?: string;
  app: Application;
  forPipelineConfig?: boolean;
  functionDef: IFunction;
  command?: IFunctionUpsertCommand; // optional, when ejecting from a wizard
  closeModal?: (functionCommand: IFunctionUpsertCommand) => void; // provided by ReactModal
  dismissModal?: (rejectReason?: any) => void; // provided by ReactModal
}

export interface ICreateFunctionButtonProps {
  app: Application;
}

export interface ICreateFunctionButtonState {
  isDisabled: boolean;
}

export class CreateFunctionButton extends React.Component<ICreateFunctionButtonProps, ICreateFunctionButtonState> {
  constructor(props: ICreateFunctionButtonProps) {
    super(props);

    const { app } = this.props;
    this.state = { isDisabled: true };
    ProviderSelectionService.isDisabled(app).then((val) => {
      this.setState({
        isDisabled: val,
      });
    });
  }

  private createFunction = (): void => {
    const { app } = this.props;

    ProviderSelectionService.selectProvider(app, 'function').then((selectedProvider) => {
      const provider = CloudProviderRegistry.getValue(selectedProvider, 'function');
      provider.CreateFunctionModal.show({
        app: app,
        application: app,
        forPipelineConfig: false,
        function: null,
        isNew: true,
      });
    });
  };

  public render() {
    const { isDisabled } = this.state;

    return (
      <div>
        {!isDisabled && (
          <button className="btn btn-sm btn-default" onClick={this.createFunction}>
            <span className="glyphicon glyphicon-plus-sign visible-lg-inline" />
            <Tooltip value="Create Function">
              <span className="glyphicon glyphicon-plus-sign visible-md-inline visible-sm-inline" />
            </Tooltip>
            <span className="visible-lg-inline"> Create Function </span>
          </button>
        )}
      </div>
    );
  }
}
