import { useCallback, useState } from 'react';

/**
 * Custom hook to manage modal visibility.
 * Provides methods to open and close a modal.
 *
 * @param {UseModalProps} [props] - Configuration options for initial modal state.
 * @param {boolean} [props.defaultOpen=false] - Initial visibility state of the modal.
 * @returns {UseModalReturn} - The current state and control methods for the modal.
 */
export type UseModalReturn = {
  open: boolean;
  show: () => void;
  close: () => void;
};

/**
 * Configuration options for the useModal hook.
 *
 * @typedef {Object} UseModalProps
 * @property {boolean} [defaultOpen=false] - Initial state of the modal.
 */
export type UseModalProps = {
  defaultOpen?: boolean;
};

/**
 * Hook to control modal visibility.
 * Returns methods to open and close a modal, and the modal's current state.
 *
 * @param {UseModalProps} [props] - The configuration for the modal.
 * @returns {UseModalReturn} - Modal visibility state and control methods.
 */
export const useModal = ({ defaultOpen = false }: UseModalProps = {}): UseModalReturn => {
  const [open, setOpen] = useState(defaultOpen);

  const show = useCallback(() => setOpen(true), [open]);
  const close = useCallback(() => setOpen(false), [open]);

  return {
    open,
    show,
    close,
  };
};
