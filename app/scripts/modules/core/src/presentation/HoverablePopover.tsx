import * as React from 'react';
import * as ReactDOM from 'react-dom';
import { Overlay, Popover, PopoverProps } from 'react-bootstrap';
import { Observable, Subject } from 'rxjs';

import { Placement } from 'core/presentation';
import { UUIDGenerator } from 'core/utils';

export interface IHoverablePopoverContentsProps extends IHoverablePopoverProps {
  // The popover contents can forcibly hide the popover by calling this function
  hidePopover: () => void;
}

export interface IHoverablePopoverProps extends React.HTMLProps<any> {
  /** The popover contents (simple mode) */
  value?: string;
  /** The popover contents (advanced mode) */
  Component?: React.ComponentType<IHoverablePopoverContentsProps>;

  /**
   * The popover contents (advanced mode)
   * @deprecated - use Component
   */
  template?: JSX.Element;

  placement?: Placement;
  /** a percent string between 0% and 100% */
  hOffsetPercent?: string;
  /** class to put on the popover content */
  className?: string;

  /** Rendered on the top of the popover content */
  title?: string;
  id?: string;

  /** Render popover contents into this container, or body if null */
  container?: JSX.Element | HTMLElement;

  delayShow?: number;
  delayHide?: number;

  onShow?: () => void;
  onHide?: () => void;
}

export interface IHoverablePopoverState {
  popoverIsOpen: boolean;
  animation: boolean;
  placementOverride?: Placement;
  PopoverRenderer?: React.ComponentType<IHoverablePopoverContentsProps>;
}

export class HoverablePopover extends React.Component<IHoverablePopoverProps, IHoverablePopoverState> {
  public static defaultProps: Partial<IHoverablePopoverProps> = {
    placement: 'top',
    id: UUIDGenerator.generateUuid(),
    delayShow: 0,
    delayHide: 300,
  };

  private target: Element;

  private mouseEvents$ = new Subject<React.SyntheticEvent<any>>();
  private hidePopoverEvents$ = new Subject();
  private destroy$ = new Subject();

  constructor(props: IHoverablePopoverProps) {
    super(props);
    this.state = { popoverIsOpen: false, animation: true };
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  public componentDidMount() {
    const shouldShowEvents = ['mouseenter', 'mouseover'];
    const showHideMouseEvents$ = this.mouseEvents$.map((event: React.MouseEvent<any>) => {
      const shouldOpen = shouldShowEvents.includes(event.type);
      const eventDelay = shouldOpen ? this.props.delayShow : this.props.delayHide;
      return { shouldOpen, eventDelay, animation: true };
    });

    const hideProgramatically$ = this.hidePopoverEvents$.map(() => {
      return { shouldOpen: false, eventDelay: 0, animation: false };
    });

    Observable.merge(showHideMouseEvents$, hideProgramatically$)
      .map(({ shouldOpen, eventDelay, animation }) => Observable.of({ shouldOpen, animation }).delay(eventDelay))
      .switchMap(result => result)
      .filter(({ shouldOpen }) => shouldOpen !== this.state.popoverIsOpen)
      .takeUntil(this.destroy$)
      .subscribe(({ shouldOpen, animation }) => this.setPopoverOpen(shouldOpen, animation));
  }

  private setPopoverOpen(popoverIsOpen: boolean, animation = true): void {
    this.setState({ animation, popoverIsOpen });
    const callback = popoverIsOpen ? this.props.onShow : this.props.onHide;
    callback && callback();
  }

  private handleMouseEvent = (e: React.SyntheticEvent<any>): void => {
    this.mouseEvents$.next(e);
  };

  private refCallback = (ref: Element): void => {
    this.target = ref;
  };

  private rendererRefCallback = (ref: React.Component): void => {
    if (ref) {
      const { clientWidth, clientHeight } = ReactDOM.findDOMNode(ref) as Element;
      const bounds = this.target.getBoundingClientRect();
      const bottomSpace = window.innerHeight - bounds.bottom;
      const rightSpace = window.innerWidth - bounds.right;

      let placementOverride: Placement;
      switch (this.props.placement) {
        case 'top':
          placementOverride = clientHeight > bounds.top && bounds.top < bottomSpace ? 'bottom' : undefined;
          break;
        case 'bottom':
          placementOverride = clientHeight > bottomSpace && bottomSpace < bounds.top ? 'top' : undefined;
          break;
        case 'left':
          placementOverride = clientWidth > bounds.left && bounds.left < rightSpace ? 'right' : undefined;
          break;
        case 'right':
          placementOverride = clientWidth > rightSpace && rightSpace < bounds.left ? 'left' : undefined;
          break;
      }
      this.setState({ placementOverride });
    }
  };

  public render() {
    const { Component, template, placement, container, hOffsetPercent, id, title, className } = this.props;
    const { popoverIsOpen, animation, placementOverride } = this.state;

    const popoverContent: JSX.Element = Component ? (
      <Component {...this.props} hidePopover={() => this.hidePopoverEvents$.next()} />
    ) : (
      template
    );

    return (
      <div
        style={{ display: 'inline' }}
        onMouseEnter={this.handleMouseEvent}
        onMouseLeave={this.handleMouseEvent}
        ref={this.refCallback}
      >
        {this.props.children}
        <Overlay
          show={popoverIsOpen}
          animation={animation}
          placement={placementOverride || placement}
          target={this.target as any}
          container={container}
        >
          <PopoverOffset
            ref={this.rendererRefCallback}
            onMouseOver={this.handleMouseEvent}
            onMouseLeave={this.handleMouseEvent}
            offsetPercent={hOffsetPercent}
            id={id}
            title={title}
            className={className}
          >
            {popoverContent}
          </PopoverOffset>
        </Overlay>
      </div>
    );
  }
}

interface IPopoverOffsetProps extends PopoverProps {
  /** offsetPercent: a percent string between 0 and 99, e.g.: `50%`, `12%` or `90%` */
  offsetPercent: string;
}

interface IPopoverOffsetState {
  offset: number;
}

/** Renders a Popover component, offset to the left or right */
class PopoverOffset extends React.Component<IPopoverOffsetProps, IPopoverOffsetState> {
  public state = {} as IPopoverOffsetState;

  public componentDidMount() {
    this.setState(this.getState(this.props));
  }

  public componentWillReceiveProps(newProps: IPopoverOffsetProps) {
    this.setState(this.getState(newProps));
  }

  public getState(props: IPopoverOffsetProps): IPopoverOffsetState {
    const { offsetPercent } = props;
    const parsePercent = (str: string) => {
      const match = /(\d+(?:\.\d+)?)%/.exec(str);
      return match ? parseFloat(match[1]) / 100 : 0.5;
    };

    const desiredPercent = parsePercent(offsetPercent);
    const currentPercent = parsePercent(props.arrowOffsetLeft as string);
    const deltaPercent = desiredPercent - currentPercent;

    const width = (ReactDOM.findDOMNode(this) as Element).clientWidth;
    const offset = 0 - width * deltaPercent;

    return { offset };
  }

  public render(): any {
    const { offsetPercent, ...rest } = this.props;
    const { offset } = this.state;

    if (offset) {
      const { style } = this.props;
      const offsetStyle = { ...style, left: style.left + offset };
      return <Popover {...rest} style={offsetStyle} arrowOffsetLeft={offsetPercent} />;
    } else {
      return <Popover {...rest} />;
    }
  }
}
