import * as React from 'react';
import * as ReactTooltip from 'react-tooltip';

export interface ITooltipProps {
  x?: number;
  y?: number;
  content?: JSX.Element;
  // unique ids are needed if we want to show multiple tooltip objects at once
  id?: string;
}

/*
* Custom tooltip component to render on top of any svg component.
* It creates an invisible pixel and uses react-tooltip library
* to point to it.
*/
export default class Tooltip extends React.Component<ITooltipProps> {
  private tooltipTarget: HTMLDivElement;

  public componentDidUpdate(prevProps: ITooltipProps) {
    const target = this.tooltipTarget;
    if (prevProps.content && !this.props.content) {
      ReactTooltip.hide(target);
    } else if (this.props.content) {
      ReactTooltip.show(target);
    }
  }

  public render() {
    const { x, y, content, id = 'tooltip' } = this.props;
    const tooltipTargetStyle = {
      left: x ? x : 0,
      top: y ? y : 0,
      height: 1,
      width: 1,
      opacity: 0,
      pointerEvents: 'none',
      position: 'absolute',
      zIndex: 10,
    } as React.CSSProperties;

    const containerStyle = {
      pointerEvents: 'none',
    } as React.CSSProperties;

    return (
      <div style={containerStyle}>
        <div data-tip={true} data-for={id} style={tooltipTargetStyle} ref={el => (this.tooltipTarget = el)} />
        <ReactTooltip id={id} type="light" border={true}>
          {content ? content : null}
        </ReactTooltip>
      </div>
    );
  }
}
