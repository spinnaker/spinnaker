import * as React from 'react';
import * as marked from 'marked';
import * as DOMPurify from 'dompurify';

export interface IMarkdownProps {
  [key: string]: any;

  /** markdown */
  message: string;
  /** optional tag */
  tag?: string;
}

/**
 * Renders markdown into a div (or some other tag)
 * Extra props are passed through to the rendered tag
 */
export class Markdown extends React.Component<IMarkdownProps, void> {
  public static defaultProps: Partial<IMarkdownProps> = {
    tag: 'div'
  };

  public render() {
    const { message, tag, ...rest } = this.props;

    const restProps = rest as React.DOMAttributes<any>;
    restProps.dangerouslySetInnerHTML = { __html: DOMPurify.sanitize(marked(message)) };

    return React.createElement(tag, restProps);
  }
}
