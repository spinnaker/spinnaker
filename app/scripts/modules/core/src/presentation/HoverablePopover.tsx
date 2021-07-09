import classnames from 'classnames';
import React from 'react';
import { Overlay, Popover, PopoverProps } from 'react-bootstrap';
import ReactDOM from 'react-dom';
import { merge as observableMerge, of as observableOf, Subject } from 'rxjs';
import { delay, filter, map, switchMap, takeUntil } from 'rxjs/operators';

import { Placement } from './Placement';
import { UUIDGenerator } from '../utils';

import './HoverablePopover.css';

export interface IHoverablePopoverContentsProps extends IHoverablePopoverProps {
  // The popover contents can forcibly hide the popover by calling this function
  hidePopover?: () => void;
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
  /** class to put on the wrapper element */
  wrapperClassName?: string;
  /** Rendered on the top of the popover content */
  title?: string;
  id?: string;

  /** Render popover contents into this container, or body if null */
  container?: JSX.Element | HTMLElement;

  delayShow?: number;
  delayHide?: number;

  onShow?: () => void;
  onHide?: () => void;
  svgMode?: boolean;
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

  private mouseEvents$ = new Subject<React.SyntheticEvent<any>>();
  private hidePopoverEvents$ = new Subject();
  private destroy$ = new Subject();
  private targetRef = React.createRef<HTMLElement>();

  constructor(props: IHoverablePopoverProps) {
    super(props);
    this.state = { popoverIsOpen: false, animation: true };
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  public componentDidMount() {
    const shouldShowEvents = ['mouseenter', 'mouseover'];
    const showHideMouseEvents$ = this.mouseEvents$.pipe(
      map((event: React.MouseEvent<any>) => {
        const shouldOpen = shouldShowEvents.includes(event.type);
        const eventDelay = shouldOpen ? this.props.delayShow : this.props.delayHide;
        return { shouldOpen, eventDelay, animation: true };
      }),
    );

    const hideProgramatically$ = this.hidePopoverEvents$.pipe(
      map(() => {
        return { shouldOpen: false, eventDelay: 0, animation: false };
      }),
    );

    observableMerge(showHideMouseEvents$, hideProgramatically$)
      .pipe(
        map(({ shouldOpen, eventDelay, animation }) => observableOf({ shouldOpen, animation }).pipe(delay(eventDelay))),
        switchMap((result) => result),
        filter(({ shouldOpen }) => shouldOpen !== this.state.popoverIsOpen),
        takeUntil(this.destroy$),
      )
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

  private rendererRefCallback = (ref: React.Component): void => {
    if (ref) {
      const { clientWidth, clientHeight } = ReactDOM.findDOMNode(ref) as Element;
      const bounds = this.targetRef.current.getBoundingClientRect();
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

  private Wrapper = ({ children, ...otherProps }: any) => {
    const { svgMode } = this.props;
    if (svgMode) {
      return (
        <g {...otherProps} ref={this.targetRef}>
          {children}
        </g>
      );
    }
    return (
      <div {...otherProps} ref={this.targetRef}>
        {children}
      </div>
    );
  };

  public render() {
    const { Component, template, placement, container, hOffsetPercent, id, title, className } = this.props;
    const { popoverIsOpen, animation, placementOverride } = this.state;
    const { Wrapper } = this;

    const popoverContent: JSX.Element = Component ? (
      <Component {...this.props} hidePopover={() => this.hidePopoverEvents$.next()} />
    ) : (
      template
    );

    return (
      <Wrapper
        className={classnames('HoverablePopover', this.props.wrapperClassName)}
        onMouseEnter={this.handleMouseEvent}
        onMouseLeave={this.handleMouseEvent}
      >
        {this.props.children}
        <Overlay
          show={popoverIsOpen}
          animation={animation}
          placement={placementOverride || placement}
          target={this.targetRef.current}
          container={container}
          shouldUpdatePosition={true}
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
      </Wrapper>
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
      const left = parseInt(`${style.left}`, 10) + offset;
      const offsetStyle = { ...style, left };
      return <Popover {...rest} style={offsetStyle} arrowOffsetLeft={offsetPercent} />;
    } else {
      return <Popover {...rest} />;
    }
  }
}
