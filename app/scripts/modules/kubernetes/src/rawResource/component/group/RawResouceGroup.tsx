import React from 'react';
import './RawResource.less';

interface IRawResourceGroupProps {
  title: string;
  resources?: IApiKubernetesResource[];
}

interface IRawResourceGroupState {
  open: boolean;
}

export class RawResourceGroup extends React.Component<IRawResourceGroupProps, IRawResourceGroupState> {
  constructor(props: IRawResourceGroupProps) {
    super(props);

    this.state = {
      open: true,
    };
  }

  public render() {
    return (
      <div className="RawResourceGroup">
        <div className="clickable sticky-header header" onClick={this.onHeaderClick.bind(this)}>
          <span className={`glyphicon pipeline-toggle glyphicon-chevron-${this.state.open ? 'down' : 'right'}`} />
          <div className="shadowed">
            <h4 className="group-title">{this.props.title}</h4>
          </div>
        </div>
        <div className={`items${this.state.open ? '' : ' hidden'}`}>{this.props.children}</div>
      </div>
    );
  }

  private onHeaderClick() {
    this.setState({
      open: !this.state.open,
    });
  }
}
