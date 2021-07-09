import React from 'react';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';
import { logger } from '../Logger';
import './CopyToClipboard.less';

export interface ICopyToClipboardProps {
  analyticsLabel?: string;
  buttonInnerNode?: React.ReactNode;
  displayText?: boolean;
  text: string;
  toolTip?: string;
  className?: string;
  stopPropagation?: boolean;
}

interface ICopyToClipboardState {
  tooltipCopy: boolean | string;
  shouldUpdatePosition: boolean;
}

/**
 * Places text in an invisible input field so we can auto-focus and select the text
 * then copy it to the clipboard onClick. Used in labels found in components like
 * ManifestStatus to make it easier to grab data from the UI.
 *
 * This component mimics utils/clipboard/copyToClipboard.component.ts but
 * since the text is placed in an invisible input its very easy to select
 * if the copy fails.
 */
export class CopyToClipboard extends React.Component<ICopyToClipboardProps, ICopyToClipboardState> {
  public static defaultProps = {
    displayText: false,
  };

  private inputRef: React.RefObject<HTMLTextAreaElement> = React.createRef();
  private mounted = false;

  constructor(props: ICopyToClipboardProps) {
    super(props);
    this.state = {
      tooltipCopy: false,
      shouldUpdatePosition: false,
    };
  }

  public componentDidMount() {
    this.mounted = true;
  }

  public componentWillUnmount(): void {
    this.mounted = false;
  }

  /**
   * Focuses on the input element and attempts to copy to the clipboard.
   * Also updates state.tooltipCopy with a success/fail message, which is
   * reset after 3s. The selection is immediately blur'd so you shouldn't
   * see much of it during the copy.
   */
  public handleClick = (e: React.SyntheticEvent): void => {
    e.preventDefault();
    if (this.props.stopPropagation) {
      e.stopPropagation();
    }

    const { analyticsLabel, text } = this.props;
    logger.log({
      category: 'Copy to Clipboard',
      action: 'copy',
      data: { label: analyticsLabel || text },
    });

    const node: HTMLTextAreaElement = this.inputRef.current;
    node.focus();
    node.select();

    const copiedText = 'Copied!';

    try {
      document.execCommand('copy');
      node.blur();
      this.setState({ tooltipCopy: copiedText, shouldUpdatePosition: true });
      window.setTimeout(this.resetToolTip, 3000);
    } catch (e) {
      node.blur();
      this.setState({ tooltipCopy: "Couldn't copy!", shouldUpdatePosition: true });
    }
  };

  private renderTooltip(children: React.ReactNode): React.ReactNode {
    const { toolTip, text = '' } = this.props;
    const { tooltipCopy, shouldUpdatePosition } = this.state;

    const persistOverlay = Boolean(tooltipCopy);
    const copy = tooltipCopy || toolTip;
    const id = `clipboardValue-${text.replace(' ', '-')}`;
    const tooltipComponent = <Tooltip id={id}>{copy}</Tooltip>;

    // Hack - shouldUpdatePosition is a valid prop, just not declared in typings
    const otherProps = {
      shouldUpdatePosition,
    };

    if (!copy) {
      return children;
    }

    return (
      <OverlayTrigger
        defaultOverlayShown={persistOverlay}
        placement="top"
        overlay={tooltipComponent}
        delayHide={250}
        {...otherProps}
      >
        {children}
      </OverlayTrigger>
    );
  }

  public resetToolTip = () => {
    this.mounted && this.setState({ tooltipCopy: false, shouldUpdatePosition: true });
  };

  public render() {
    const { buttonInnerNode, text = '', className = 'btn btn-xs btn-default clipboard-btn' } = this.props;

    const copyButton = (
      <button onClick={this.handleClick} className={className} aria-label="Copy to clipboard">
        {buttonInnerNode ? buttonInnerNode : <span className="glyphicon glyphicon-copy" />}
      </button>
    );

    return (
      <>
        <textarea
          onChange={(e) => e} // no-op to prevent warnings
          ref={this.inputRef}
          value={text}
          tabIndex={-1}
          style={{ zIndex: -1, position: 'fixed', opacity: 0, top: 0, left: 0 }}
        />
        {this.renderTooltip(copyButton)}
      </>
    );
  }
}
