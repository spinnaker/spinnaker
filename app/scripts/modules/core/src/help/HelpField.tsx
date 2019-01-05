import * as React from 'react';
import * as ReactGA from 'react-ga';
import * as DOMPurify from 'dompurify';

import { HelpContentsRegistry } from 'core/help';
import { HoverablePopover, Placement } from 'core/presentation';

export interface IHelpFieldProps {
  id?: string;
  fallback?: string;
  content?: string;
  placement?: Placement;
  expand?: boolean;
  label?: string;
}

export class HelpField extends React.PureComponent<IHelpFieldProps> {
  public static defaultProps: IHelpFieldProps = {
    placement: 'top',
  };

  private popoverShownStart: number;

  private renderContents(id: string, fallback: string, content: string): JSX.Element {
    let contentString = content;
    if (id && !contentString) {
      contentString = HelpContentsRegistry.getHelpField(id) || fallback;
    }

    const config = { ADD_ATTR: ['target'] }; // allow: target="_blank"
    return <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(contentString, config) }} />;
  }

  private onShow = (): void => {
    this.popoverShownStart = Date.now();
  };

  private onHide = (): void => {
    if (Date.now() - this.popoverShownStart > 500) {
      ReactGA.event({ action: 'Help contents viewed', category: 'Help', label: this.props.id || this.props.content });
    }
  };

  public render() {
    const { placement, label, expand, id, fallback, content } = this.props;
    const contents = this.renderContents(id, fallback, content);

    const icon = <i className="small glyphicon glyphicon-question-sign" />;

    const popover = (
      <HoverablePopover placement={placement} template={contents} onShow={this.onShow} onHide={this.onHide}>
        <a className="clickable help-field"> {label || icon} </a>
      </HoverablePopover>
    );

    if (label) {
      return <div className="text-only">{!expand && contents && popover}</div>;
    } else {
      const expanded = <div className="help-contents small"> {contents} </div>;

      return (
        <div style={{ display: 'inline-block' }}>
          {!expand && contents && popover}
          {expand && contents && expanded}
        </div>
      );
    }
  }
}
