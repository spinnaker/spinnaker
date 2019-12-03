import React from 'react';
import { HtmlRenderer, Parser } from 'commonmark';
import DOMPurify from 'dompurify';
import './Markdown.less';

export interface IMarkdownProps {
  [key: string]: any;

  /** markdown */
  message: string;

  /** optional tag */
  tag?: string;

  /** The className(s) to apply to the tag (.Markdown class is always applied) */
  className?: string;
}

/**
 * Renders markdown into a div (or some other tag)
 * Extra props are passed through to the rendered tag
 */
export function Markdown(props: IMarkdownProps) {
  const { message, tag: tagProp, className: classNameProp, tag = 'div', ...rest } = props;

  const parser = React.useMemo(() => new Parser(), []);
  const renderer = React.useMemo(() => new HtmlRenderer(), []);
  const className = `Markdown ${classNameProp || ''}`;

  if (message == null) {
    return null;
  }

  const restProps = rest as React.DOMAttributes<any>;
  const parsed = parser.parse(message.toString());
  const rendered = renderer.render(parsed);
  restProps.dangerouslySetInnerHTML = { __html: DOMPurify.sanitize(rendered) };

  return React.createElement(tag, { ...restProps, className });
}
