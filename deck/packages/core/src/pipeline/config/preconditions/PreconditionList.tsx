import React from 'react';

import { EditPreconditionModal } from './EditPreconditionModal';
import type { Application } from '../../../application';
import type { IStage } from '../../../domain';
import type { IPrecondition } from './preconditionTypes';
import { getPreconditionTypeLabel } from './preconditionTypes';
import { robotToHuman } from '../../../presentation/robotToHumanFilter/robotToHuman.filter';
import { StageConfigField } from '../stages/common';

import './preconditionList.directive.less';

export interface IPreconditionListProps {
  application: Application;
  onChange: (preconditions: IPrecondition[]) => void;
  preconditions: IPrecondition[];
  strategy: boolean;
  upstreamStages: IStage[];
}

export class PreconditionList extends React.Component<IPreconditionListProps> {
  private editPrecondition = (precondition?: IPrecondition) => {
    EditPreconditionModal.show({
      application: this.props.application,
      precondition,
      strategy: this.props.strategy,
      upstreamStages: this.props.upstreamStages,
    })
      .then((updatedPrecondition) => {
        if (!precondition) {
          this.props.onChange(this.props.preconditions.concat(updatedPrecondition));
          return;
        }

        this.props.onChange(
          this.props.preconditions.map((current) => (current === precondition ? updatedPrecondition : current)),
        );
      })
      .catch(() => {});
  };

  private removePrecondition = (precondition: IPrecondition) => {
    this.props.onChange(this.props.preconditions.filter((current) => current !== precondition));
  };

  private renderContext(precondition: IPrecondition) {
    return Object.keys(precondition.context || {}).map((key) => (
      <div key={key}>
        <strong>{robotToHuman(key)}: </strong>
        {String(precondition.context[key])}
      </div>
    ));
  }

  public render() {
    return (
      <div className="form-horizontal">
        <StageConfigField label="Preconditions">
          <table className="table table-condensed">
            <thead>
              <tr>
                <th>Type</th>
                <th className="precondition-details">Details</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {this.props.preconditions.map((precondition, index) => (
                <tr key={index}>
                  <td>{getPreconditionTypeLabel(precondition.type)}</td>
                  <td className="precondition-details">
                    {this.renderContext(precondition)}
                    <strong>Fail Pipeline: </strong>
                    {String(precondition.failPipeline)}
                  </td>
                  <td>
                    <button
                      type="button"
                      className="btn btn-xs btn-link"
                      data-action="edit"
                      onClick={() => this.editPrecondition(precondition)}
                    >
                      <span className="glyphicon glyphicon-edit" />
                    </button>
                    <button
                      type="button"
                      className="btn btn-xs btn-link pad-left"
                      data-action="remove"
                      onClick={() => this.removePrecondition(precondition)}
                    >
                      <span className="glyphicon glyphicon-trash" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={7}>
                  <button type="button" className="btn btn-block add-new" onClick={() => this.editPrecondition()}>
                    <span className="glyphicon glyphicon-plus-sign" /> Add Precondition
                  </button>
                </td>
              </tr>
            </tfoot>
          </table>
        </StageConfigField>
      </div>
    );
  }
}
