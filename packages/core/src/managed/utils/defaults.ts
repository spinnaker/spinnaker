import type { IManagedDeliveryURLs } from '../../config/settings';
import { SETTINGS } from '../../config/settings';
import type { IHoverablePopoverProps } from '../../presentation';
import type { ISpinnerProps } from '../../widgets';

export const TOOLTIP_DELAY_SHOW = 400;
export const TOOLTIP_DELAY_HIDE = 100;
export const tooltipShowHideProps: Partial<IHoverablePopoverProps> = {
  delayShow: TOOLTIP_DELAY_SHOW,
  delayHide: TOOLTIP_DELAY_HIDE,
};
export const MODAL_MAX_WIDTH = 750;

export const ACTION_BUTTON_CLASS_NAME = 'btn btn-default md-btn md-action-button';

export const spinnerProps: ISpinnerProps = {
  size: 'medium',
  fullWidth: true,
};

export const ABSOLUTE_TIME_FORMAT = 'yyyy-MM-dd HH:mm:ss ZZZZ';

export const MD_CATEGORY = 'ManagedDelivery';

const DOCS_URLS: IManagedDeliveryURLs = {
  root: 'https://www.spinnaker.io/guides/user/managed-delivery',
  pinning: 'https://www.spinnaker.io/guides/user/managed-delivery/pinning/',
  gettingStarted: 'https://www.spinnaker.io/guides/user/managed-delivery/getting-started/',
  markAsBad: 'https://www.spinnaker.io/guides/user/managed-delivery/marking-as-bad/',
  resourceStatus: 'https://www.spinnaker.io/guides/user/managed-delivery/resource-status/',
};

export const getDocsUrl = (doc: keyof IManagedDeliveryURLs): string | undefined => {
  return SETTINGS.managedDelivery?.urls?.[doc] || DOCS_URLS[doc];
};
