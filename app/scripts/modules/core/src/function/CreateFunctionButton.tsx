import React from 'react';

import { Application } from 'core/application';
import { CloudProviderRegistry, ProviderSelectionService } from 'core/cloudProvider';
import { IFunction } from 'core/domain';
import { IFunctionUpsertCommand } from 'core/function';
import { ReactInjector } from 'core/reactShims';
import { IModalComponentProps, Tooltip } from 'core/presentation';

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

export class CreateFunctionButton extends React.Component<ICreateFunctionButtonProps> {
  private createFunction = (): void => {
    const { skinSelectionService } = ReactInjector;
    const { app } = this.props;

    ProviderSelectionService.selectProvider(app, 'function').then(selectedProvider => {
      skinSelectionService.selectSkin(selectedProvider).then(selectedSkin => {
        const provider = CloudProviderRegistry.getValue(selectedProvider, 'function', selectedSkin);
        provider.CreateFunctionModal.show({
          app: app,
          application: app,
          forPipelineConfig: false,
          function: null,
          isNew: true,
        });
      });
    });
  };

  public render() {
    return (
      <div>
        <button className="btn btn-sm btn-default" onClick={this.createFunction}>
          <span className="glyphicon glyphicon-plus-sign visible-lg-inline" />
          <Tooltip value="Create Function">
            <span className="glyphicon glyphicon-plus-sign visible-md-inline visible-sm-inline" />
          </Tooltip>
          <span className="visible-lg-inline"> Create Function </span>
        </button>
      </div>
    );
  }
}
