import React from 'react';

import { INavigationPage } from './PageNavigator';

interface IPageSectionProps {
  pageKey: string;
  label: string;
  badge?: string;
  visible?: boolean;
  noWrapper?: boolean;
  updatePagesConfig?: (page: INavigationPage) => void;
}

export class PageSection extends React.Component<IPageSectionProps> {
  constructor(props: IPageSectionProps) {
    super(props);
  }

  public componentDidUpdate(prevProps: IPageSectionProps): void {
    const { badge, pageKey, label, updatePagesConfig, visible } = this.props;
    if (
      prevProps.visible !== this.props.visible ||
      prevProps.badge !== this.props.badge ||
      prevProps.label !== this.props.label
    ) {
      updatePagesConfig &&
        updatePagesConfig({
          key: pageKey,
          label,
          visible: visible !== false,
          badge,
        });
    }
  }

  public render(): JSX.Element {
    const { children, pageKey, label, noWrapper, visible } = this.props;

    return visible !== false ? (
      <div className="page-section">
        <div className="page-subheading flex-1" data-page-id={pageKey}>
          <h4 className="sticky-header">{label}</h4>
          <div className={noWrapper ? 'no-wrapper' : 'section-body'} data-page-content={pageKey}>
            {children}
          </div>
        </div>
      </div>
    ) : (
      <></>
    );
  }
}
