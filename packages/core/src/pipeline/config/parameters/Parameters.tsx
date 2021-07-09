import React from 'react';
import { arrayMove, SortableContainer, SortableContainerProps, SortableElement, SortEnd } from 'react-sortable-hoc';

import { IParameterProps, Parameter } from './Parameter';
import { IParameter } from '../../../domain';
import { HelpField } from '../../../help';

export interface IParametersState {
  allParametersPinned: boolean;
}

export interface IParametersProps {
  parameters: IParameter[];
  pipelineName: string;
  addParameter: () => void;
  removeParameter: (index: number) => void;
  updateParameter: (index: number, changes: { [key: string]: any }) => void;
  updateAllParameters: (parameters: IParameter[]) => void;
}

export class Parameters extends React.Component<IParametersProps, IParametersState> {
  constructor(props: IParametersProps) {
    super(props);
    this.state = {
      allParametersPinned: false,
    };
  }

  public componentDidMount() {
    this.setPinAllParametersState();
  }

  private setPinAllParametersState = (): void => {
    this.setState({ allParametersPinned: (this.props.parameters || []).every((p) => p.pinned) });
  };

  private togglePins = (): void => {
    const parameters = this.props.parameters.slice(0);
    const { allParametersPinned } = this.state;
    parameters
      .filter((param) => !param.inherited)
      .forEach((param) => {
        param.pinned = !allParametersPinned;
      });
    this.props.updateAllParameters(parameters);
    this.setPinAllParametersState();
  };

  private addParameter = (): void => {
    this.props.addParameter();
    this.setPinAllParametersState();
  };

  private removeParameter = (index: number): void => {
    this.props.removeParameter(index);
    this.setPinAllParametersState();
  };

  private updateParameter = (index: number, changes: { [key: string]: any }): void => {
    this.props.updateParameter(index, changes);
    this.setPinAllParametersState();
  };

  private handleSortEnd = (sortEnd: SortEnd): void => {
    const parameters = arrayMove(this.props.parameters.slice(0), sortEnd.oldIndex, sortEnd.newIndex);
    this.props.updateAllParameters(parameters);
  };

  public render(): JSX.Element {
    const { parameters, pipelineName } = this.props;
    const { allParametersPinned } = this.state;
    return (
      <>
        <SortableParameters
          parameters={parameters}
          allParametersPinned={allParametersPinned}
          setPinAllParametersState={this.setPinAllParametersState}
          togglePins={this.togglePins}
          removeParameter={this.removeParameter}
          updateParameter={this.updateParameter}
          isMultiple={parameters && parameters.length > 1}
          onSortEnd={this.handleSortEnd}
          lockAxis={'y'}
          useDragHandle={true}
        />
        <div className="row">
          <div className="col-md-12">
            {parameters && !parameters.length && <p>You don't have any parameters configured for {pipelineName}</p>}
            <button className={'btn btn-block btn-add-trigger add-new'} onClick={this.addParameter}>
              <span className="glyphicon glyphicon-plus-sign" /> Add Parameter
            </button>
          </div>
        </div>
      </>
    );
  }
}

const SortableParameterElement = SortableElement((props: IParameterProps) => <Parameter {...props} />);

interface ISortableParametersProps extends SortableContainerProps {
  parameters: IParameter[];
  allParametersPinned: boolean;
  setPinAllParametersState: () => void;
  togglePins: () => void;
  removeParameter: (index: number) => void;
  updateParameter: (index: number, changes: { [key: string]: any }) => void;
  isMultiple: boolean;
}

const SortableParameters = SortableContainer((props: ISortableParametersProps) => (
  <div>
    <div className="checkbox">
      <label>
        <input type="checkbox" checked={props.allParametersPinned} onChange={props.togglePins} />
        <span className="label-text sm-label-right">Pin all parameters </span>
        <HelpField id="pipeline.config.parameter.pinAll" />
      </label>
    </div>
    {props.parameters &&
      props.parameters.map((parameter, index) => {
        return (
          <SortableParameterElement
            key={index}
            index={index}
            {...parameter}
            removeParameter={() => props.removeParameter(index)}
            setPinAllParametersState={props.setPinAllParametersState}
            updateParameterField={(changes: { [key: string]: any }) => props.updateParameter(index, changes)}
            isMultiple={props.isMultiple}
          />
        );
      })}
  </div>
));
