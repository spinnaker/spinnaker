import * as React from 'react';
import * as ReactDOM from 'react-dom';
import { Overlay, Popover, PopoverProps } from 'react-bootstrap';
import { Observable, Subject, Subscription } from 'rxjs';
import { BindAll } from 'lodash-decorators';

import { Placement } from 'core/presentation';
import { UUIDGenerator } from 'core/utils';

export interface IHoverablePopoverProps {
  /** The popover contents (simple mode) */
  value?: string;
  /** The popover contents (advanced mode) */
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
  placementOverride?: Placement;
  popoverIsOpen: boolean;
  PopoverRenderer?: React.ComponentClass<PopoverProps> | React.StatelessComponent<PopoverProps>;
}

@BindAll()
export class HoverablePopover extends React.Component<IHoverablePopoverProps, IHoverablePopoverState> {

  public static defaultProps: Partial<IHoverablePopoverProps> = {
    placement: 'top',
    id: UUIDGenerator.generateUuid(),
    delayShow: 0,
    delayHide: 300,
  };

  private target: Element;

  private mouseEvents$: Subject<React.SyntheticEvent<any>>;
  private showHide$: Observable<boolean>;
  private showHideSubscription: Subscription;

  /**
   * Renders a Popover offset to the left or right.
   * offetPercent: a percent string between 0 and 99, e.g.: `50%`, `12%` or `90%`
   */
  private static popoverOffsetRenderer(offsetPercent: string) {
    interface IOffsetState {
      offset: number,
    }

    return class extends React.Component<PopoverProps, IOffsetState> {
      public state = {} as IOffsetState;

      public componentDidMount() {
        this.setState(this.getState(this.props));
      }

      public componentWillReceiveProps(newProps: PopoverProps) {
        this.setState(this.getState(newProps));
      }

      public getState(props: PopoverProps): IOffsetState {
        const parsePercent = (str: string) => {
          const match = /(\d+(?:\.\d+)?)%/.exec(str);
          return match ? parseFloat(match[1]) / 100 : .5;
        };

        const desiredPercent = parsePercent(offsetPercent);
        const currentPercent = parsePercent(props.arrowOffsetLeft as string);
        const deltaPercent = (desiredPercent - currentPercent);

        const width = ReactDOM.findDOMNode(this).clientWidth;
        const offset = 0 - (width * deltaPercent);

        return { offset: offset };
      }

      public render(): any {
        const { offset } = this.state;
        if (offset) {
          const { style } = this.props;
          const offsetStyle = Object.assign({}, style, { left: style.left + offset });
          return <Popover {...this.props} style={offsetStyle} arrowOffsetLeft={offsetPercent} />
        } else {
          return <Popover {...this.props} />
        }
      }
    }
  }

  constructor(props: IHoverablePopoverProps) {
    super(props);

    this.state = {
      popoverIsOpen: false,
      PopoverRenderer: HoverablePopover.popoverOffsetRenderer(props.hOffsetPercent),
    };
  }

  public componentWillUnmount() {
    this.mouseEvents$.unsubscribe();
    this.showHideSubscription.unsubscribe();
  }

  public componentDidMount() {
    this.mouseEvents$ = new Subject();
    this.showHide$ = this.mouseEvents$
      .map((event: React.MouseEvent<any>) => {
        const shouldOpen = ['mouseenter', 'mouseover'].includes(event.type);
        const isChanging = (shouldOpen !== this.state.popoverIsOpen);
        const eventDelay = shouldOpen ? this.props.delayShow : this.props.delayHide;
        return Observable.of(shouldOpen).delay(isChanging ? eventDelay : 0);
      })
      .switchMap(result => result)
      .distinctUntilChanged();

    this.showHideSubscription = this.showHide$.subscribe(shouldOpen => this.setPopoverOpen(shouldOpen));
  }

  private setPopoverOpen(popoverIsOpen: boolean): void {
    this.setState({ popoverIsOpen });
    const callback = popoverIsOpen ? this.props.onShow : this.props.onHide;
    callback && callback();
  }

  private handleMouseEvent(e: React.SyntheticEvent<any>): void {
    this.mouseEvents$.next(e);
  }

  private refCallback(ref: Element): void {
    this.target = ref;
  }

  private rendererRefCallback(ref: React.Component): void {
    if (ref) {
      const { clientWidth, clientHeight } = ReactDOM.findDOMNode(ref);
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
  }

  public render() {
    const PopoverContents = this.props.template;
    const PopoverRenderer = this.state.PopoverRenderer;

    return (
      <g onMouseEnter={this.handleMouseEvent} onMouseLeave={this.handleMouseEvent} ref={this.refCallback}>
        {this.props.children}
        <Overlay show={this.state.popoverIsOpen} placement={this.state.placementOverride || this.props.placement} target={this.target as any} container={this.props.container}>
          <PopoverRenderer
            ref={this.rendererRefCallback}
            onMouseOver={this.handleMouseEvent}
            onMouseLeave={this.handleMouseEvent}
            id={this.props.id}
            title={this.props.title}
            className={this.props.className}
          >
            {PopoverContents}
          </PopoverRenderer>
        </Overlay>
      </g>
    );
  }
}
