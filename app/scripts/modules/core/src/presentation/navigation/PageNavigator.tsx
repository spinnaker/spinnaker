import { isFunction, throttle } from 'lodash';
import React from 'react';

import { ReactInjector } from '../../reactShims';
import { ScrollToService } from '../../utils/scrollTo/scrollTo.service';
import { UUIDGenerator } from '../../utils/uuid.service';

export interface INavigationPage {
  key: string;
  label: string;
  visible?: boolean;
  badge?: string;
}

export interface IPageNavigatorProps {
  scrollableContainer: string;
  deepLinkParam?: string;
  hideNavigation?: boolean;
}

export interface IPageNavigatorState {
  id: string;
  currentPageKey: string;
  pages: INavigationPage[];
}

export class PageNavigator extends React.Component<IPageNavigatorProps, IPageNavigatorState> {
  private element: JQuery;
  private container: any;
  private navigator: any;

  constructor(props: IPageNavigatorProps) {
    super(props);
    this.state = {
      id: UUIDGenerator.generateUuid(),
      currentPageKey: null,
      pages: [],
    };
  }

  public componentDidMount(): void {
    const { children, deepLinkParam, hideNavigation, scrollableContainer } = this.props;
    this.container = this.element.closest(scrollableContainer);
    if (isFunction(this.container.bind) && !hideNavigation) {
      this.container.bind(
        this.getEventKey(),
        throttle(() => this.handleScroll(), 20),
      );
    }
    this.navigator = this.element.find('.page-navigation');
    if (deepLinkParam && ReactInjector.$stateParams[deepLinkParam]) {
      this.setCurrentSection(ReactInjector.$stateParams[deepLinkParam]);
    }

    const pages = React.Children.map(children, (child: any) => {
      if (child.props.pageKey && child.props.label) {
        return {
          key: child.props.pageKey,
          label: child.props.label,
          visible: child.props.visible !== false,
          badge: child.props.badge,
        };
      }
      return null;
    });
    this.setState({
      pages,
      currentPageKey: pages.length > 0 ? pages[0].key : null,
    });
  }

  public componentWillUnmount(): void {
    if (isFunction(this.container.unbind) && !this.props.hideNavigation) {
      this.container.unbind(this.getEventKey());
    }
  }

  private setCurrentSection(key: string): void {
    this.setState({ currentPageKey: key });
    this.syncLocation(key);
    ScrollToService.scrollTo(`[data-page-id=${key}]`, this.props.scrollableContainer, this.container.offset().top);
    this.container.find('.highlighted').removeClass('highlighted');
    this.container.find(`[data-page-id=${key}]`).addClass('highlighted');
  }

  private getEventKey(): string {
    return `scroll.pageNavigation.${this.state.id}`;
  }

  private handleScroll(): void {
    const navigatorRect = this.element.get(0).getBoundingClientRect();
    const scrollableContainerTop = this.container.get(0).getBoundingClientRect().top;

    const currentPage = this.state.pages.find((p) => {
      const content = this.container.find(`[data-page-content=${p.key}]`);
      if (content.length) {
        return content.get(0).getBoundingClientRect().bottom > scrollableContainerTop;
      }
      return false;
    });
    if (currentPage) {
      this.setState({ currentPageKey: currentPage.key });
      this.syncLocation(currentPage.key);
      this.navigator.find('li').removeClass('current');
      this.navigator.find(`[data-page-navigation-link=${currentPage.key}]`).addClass('current');
    }

    if (navigatorRect.top < scrollableContainerTop) {
      this.navigator.css({
        position: 'fixed',
        width: this.navigator.get(0).getBoundingClientRect().width,
        top: scrollableContainerTop,
      });
    } else {
      this.navigator.css({
        position: 'relative',
        top: 0,
        width: '100%',
      });
    }
  }

  private syncLocation(key: string): void {
    const { deepLinkParam } = this.props;
    if (deepLinkParam) {
      ReactInjector.$state.go('.', { [deepLinkParam]: key }, { notify: false, location: 'replace' });
    }
  }

  private refCallback = (element: HTMLDivElement): void => {
    if (element) {
      this.element = $(element);
    }
  };

  private updatePagesConfig(page: INavigationPage): void {
    const pages = [...this.state.pages];
    const pageConfig = pages.find((p) => p.key === page.key);
    if (pageConfig) {
      pageConfig.badge = page.badge;
      pageConfig.label = page.label;
      pageConfig.visible = page.visible;
      this.setState({ pages });
    }
  }

  public render(): JSX.Element {
    const { children, hideNavigation } = this.props;
    const { currentPageKey, pages } = this.state;
    const updatedChildren = React.Children.map(children, (child: any) =>
      React.cloneElement(child, { updatePagesConfig: (p: INavigationPage) => this.updatePagesConfig(p) }),
    );
    return (
      <div className="page-navigator">
        <div className="row flex-1" ref={this.refCallback}>
          {!hideNavigation && (
            <div className="col-md-3 hidden-sm hidden-xs">
              <ul className="page-navigation">
                {pages.map((page: INavigationPage, index: number) => {
                  return (
                    page.visible && (
                      <li
                        key={index}
                        data-page-navigation-link={page.key}
                        className={currentPageKey === page.key ? 'current' : ''}
                      >
                        <a onClick={() => this.setCurrentSection(page.key)}>
                          {page.label}
                          {page.badge && <span> {'(' + page.badge + ')'}</span>}
                        </a>
                      </li>
                    )
                  );
                })}
              </ul>
            </div>
          )}
          <div className={'col-md-' + (hideNavigation ? '12' : '9') + ' col-sm-12'}>
            <div className="sections">{updatedChildren}</div>
          </div>
        </div>
      </div>
    );
  }
}
