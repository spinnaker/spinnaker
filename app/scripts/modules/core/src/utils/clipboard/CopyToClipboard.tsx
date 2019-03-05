import * as React from 'react';
import * as ReactGA from 'react-ga';
import { OverlayTrigger, Tooltip } from 'react-bootstrap';

import './CopyToClipboard.less';

export interface ICopyToClipboardProps {
  analyticsLabel?: string;
  displayText?: boolean;
  text: string;
  toolTip: string;
  className?: string;
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

  private inputRef: React.RefObject<HTMLInputElement> = React.createRef();
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

    const { analyticsLabel, text } = this.props;
    ReactGA.event({
      category: 'Copy to Clipboard',
      action: 'copy',
      label: analyticsLabel || text,
    });

    const node: HTMLInputElement = this.inputRef.current;
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

  public resetToolTip = () => {
    this.mounted && this.setState({ tooltipCopy: false, shouldUpdatePosition: true });
  };

  public render() {
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

    return (
      <>
        <input
          onChange={e => e} // no-op to prevent warnings
          ref={this.inputRef}
          value={text}
          type="text"
          style={{ zIndex: -1, position: 'fixed', opacity: 0 }}
        />
        <OverlayTrigger
          defaultOverlayShown={persistOverlay}
          placement="top"
          overlay={tooltipComponent}
          delayHide={250}
          {...otherProps}
        >
          <button
            onClick={this.handleClick}
            className="btn btn-xs btn-default clipboard-btn"
            uib-tooltip={toolTip}
            aria-label="Copy to clipboard"
          >
            <span className="glyphicon glyphicon-copy" />
          </button>
        </OverlayTrigger>
      </>
    );
  }
}
