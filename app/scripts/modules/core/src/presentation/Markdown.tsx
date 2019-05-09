import * as React from 'react';
import { HtmlRenderer, Parser } from 'commonmark';
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
export class Markdown extends React.Component<IMarkdownProps> {
  public static defaultProps: Partial<IMarkdownProps> = {
    tag: 'div',
  };

  private parser: Parser = new Parser();
  private renderer: HtmlRenderer = new HtmlRenderer();

  public render() {
    const { message, tag, ...rest } = this.props;

    if (message == null) {
      return null;
    }

    const restProps = rest as React.DOMAttributes<any>;
    const parsed = this.parser.parse(message.toString());
    const rendered = this.renderer.render(parsed);
    restProps.dangerouslySetInnerHTML = { __html: DOMPurify.sanitize(rendered) };

    return React.createElement(tag, restProps);
  }
}
