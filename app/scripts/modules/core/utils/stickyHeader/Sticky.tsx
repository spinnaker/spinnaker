import * as React from 'react';
import { Subscription } from 'rxjs/Subscription';
import { $log } from 'ngimport';
import { omit, throttle } from 'lodash';
import autoBindMethods from 'class-autobind-decorator';

import { IStickyContext, StickyContainer } from './StickyContainer';

interface IProps extends React.HTMLAttributes<HTMLElement> {
  topOffset?: number;
  stickyIf?: () => boolean;
}

interface IState {
  passthroughProps: React.HTMLProps<HTMLDivElement>;
  width: number;
  top: number;
  zIndex: number;
  isSticky: boolean;
}

@autoBindMethods
export class Sticky extends React.Component<IProps, IState> {
  public static defaultProps: Partial<IProps> = {
    topOffset: 0
  };

  public static contextTypes: React.ValidationMap<IStickyContext> = {
    stickyContainer: React.PropTypes.instanceOf(StickyContainer)
  };

  private static RECOMPUTE_EVENTS = ['resize', 'scroll', 'touchstart', 'touchmove', 'touchend'];

  private stickyContainerElement: HTMLElement;
  private stickyElement: HTMLElement;
  private sectionPaddingTop = 0;
  private stickySubscription: Subscription;


  constructor(props: IProps, context: IStickyContext) {
    super(props, context);

    this.state = {
      passthroughProps: this.passthroughProps(props),
      width: undefined,
      top: undefined,
      zIndex: 5,
      isSticky: false
    };

    if (!context.stickyContainer) {
      $log.warn('No parent <StickyContainer> component; <Sticky> component will not stick.');
      return;
    }

    this.recomputeState = throttle(this.recomputeState, 50, {trailing: true});
  }

  private setContainerElement(element: HTMLElement): void {
      this.stickyContainerElement = element;
      if (!this.props.stickyIf || this.props.stickyIf()) {
        this.addEventListeners(this.recomputeState);
        this.recomputeState();
      }
  }

  private outerHeight(el: HTMLElement): number {
    const style = getComputedStyle(el);
    return el.offsetHeight + parseInt(style.marginTop, 10) + parseInt(style.marginBottom, 10);
  }

  private passthroughProps(props: IProps): React.HTMLProps<HTMLDivElement> {
    return omit(props, ['topOffset', 'notifyOnly', 'stickyIf', 'className', 'style']);
  }

  private recomputeState = (): void => {
    const parentElement = this.stickyElement.parentElement,
          sectionRect = parentElement.getBoundingClientRect(),
          sectionTop = sectionRect.top,
          sectionBottom = sectionRect.bottom,
          windowHeight = window.innerHeight,
          containerTop = this.stickyContainerElement.offsetTop + this.props.topOffset,
          topBreak = sectionTop - containerTop,
          isSticky = (!(sectionBottom < 0 || sectionTop > windowHeight) && topBreak < 0 && sectionBottom > containerTop);

    let width: number,
        top: number,
        zIndex = 5,
        updatedParentPaddingTop = 0;

    // If the element is supposed to be sticky, calculate the needed layout values
    if (isSticky) {
      const stickyElementRect = this.stickyElement.getBoundingClientRect();
      const stickyElementWidth = stickyElementRect.width;
      const stickyElementHeight = this.outerHeight(this.stickyElement);

      updatedParentPaddingTop = stickyElementHeight;

      if (containerTop + stickyElementHeight > sectionBottom) {
        top = sectionBottom - stickyElementHeight;
        zIndex = 4;
      } else {
        top = containerTop;
      }

      width = stickyElementWidth;
    }

    // Update the parent padding when it changed (either add padding
    // for the height of the sticky element, or reset the height when
    // the element is no longer sicky)
    if (updatedParentPaddingTop !== this.sectionPaddingTop) {
      this.sectionPaddingTop = updatedParentPaddingTop;
      parentElement.style.paddingTop = `${this.sectionPaddingTop}px`;
    }

    if (isSticky !== this.state.isSticky ||
        width !== this.state.width ||
        top !== this.state.top ||
        zIndex !== this.state.zIndex) {
      this.setState({ width, top, isSticky, zIndex });
    }
  }

  private addEventListeners(callback: () => void): void {
    if (this.stickyContainerElement) {
      Sticky.RECOMPUTE_EVENTS.forEach((event) => {
        this.stickyContainerElement.addEventListener(event, callback);
      });
    }
  }

  private removeEventListeners(callback: () => void): void {
    if (this.stickyContainerElement) {
      Sticky.RECOMPUTE_EVENTS.forEach((event) => {
        this.stickyContainerElement.removeEventListener(event, callback);
      });
    }
  }

  private refCallback(element: HTMLElement): void {
    this.stickyElement = element;
  }

  public componentDidMount(): void {
    if (this.context.stickyContainer.element) {
      this.setContainerElement(this.context.stickyContainer.element);
    } else {
      this.stickySubscription = this.context.stickyContainer.elementMounted.subscribe(this.setContainerElement);
    }
  }

  public componentWillReceiveProps(nextProps: IProps): void {
    if (nextProps.stickyIf !== this.props.stickyIf) {
      if (nextProps.stickyIf) {
        this.addEventListeners(this.recomputeState);
      } else {
        this.removeEventListeners(this.recomputeState);
      }
    }
    this.setState({passthroughProps: this.passthroughProps(nextProps)});
  }

  public componentWillUnmount(): void {
    this.removeEventListeners(this.recomputeState);
    if (this.stickySubscription) {
      this.stickySubscription.unsubscribe();
    }
  }

  public render(): React.ReactElement<Sticky> {
    let className = this.props.className || '';
    if (this.state.isSticky) {
      className += ' heading-sticky';
    }
    const style = this.props.style || {};
    style.top = this.state.top;
    style.width = this.state.width;
    style.zIndex = this.state.zIndex;

    return (
      <div {...this.state.passthroughProps as {}} className={className} style={style} ref={this.refCallback}>
        {this.props.children}
      </div>
    );
  }
}
