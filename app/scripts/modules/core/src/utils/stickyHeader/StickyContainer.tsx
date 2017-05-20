import * as React from 'react';
import * as PropTypes from 'prop-types';
import { Subject } from 'rxjs';
import autoBindMethods from 'class-autobind-decorator';

export interface IStickyContext {
  'stickyContainer': StickyContainer
}

@autoBindMethods
export class StickyContainer extends React.Component<any, any> {
  public static childContextTypes = {
    stickyContainer: PropTypes.any
  };

  public element: HTMLElement;
  public elementMounted: Subject<HTMLDivElement> = new Subject<HTMLDivElement>();
  private elementMountedAlready = false;

  constructor(props: any, context: IStickyContext) {
    super(props, context);
  }

  public refCallback(element: HTMLDivElement): void {
    this.element = element;
    if (!this.elementMountedAlready) {
      this.elementMounted.next(element);
      this.elementMountedAlready = true;
    }
  }

  public getChildContext(): IStickyContext {
    return {
      stickyContainer: this
    };
  }

  public render() {
    return (
      <div {...this.props} ref={this.refCallback}>
        {this.props.children}
      </div>
    );
  }
}
