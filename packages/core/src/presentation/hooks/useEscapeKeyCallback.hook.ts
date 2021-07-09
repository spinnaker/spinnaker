import { useEventListener } from './useEventListener.hook';

export function useEscapeKeyPressed(callback: () => any) {
  useEventListener(document.body, 'keyup', (e: KeyboardEvent) => e.key === 'Escape' && callback());
}
