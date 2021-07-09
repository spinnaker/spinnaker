import { IScope } from 'angular';
import { sortBy, throttle } from 'lodash';
import { $rootScope, $timeout } from 'ngimport';

interface IViewPlacement {
  top: number;
  elem: string;
}

interface IWaypoint {
  lastWindow: IViewPlacement[];
  top: number;
  direction: string;
  offset?: number;
  container?: JQuery;
  scrollEnabled?: boolean;
}

export class WaypointService {
  private static waypointRegistry: { [key: string]: IWaypoint } = Object.create(null);

  public static registerWaypointContainer(elementScope: IScope, element: JQuery, key: string, offset: number): void {
    this.waypointRegistry[key] = this.waypointRegistry[key] || Object.create(null);
    this.waypointRegistry[key].container = element;
    this.waypointRegistry[key].offset = offset;
    this.enableWaypointEvent(element, key);
    if (elementScope) {
      elementScope.$on('$destroy', () => {
        this.disableWaypointEvent(key);
      });
    }
  }

  private static enableWaypointEvent(element: JQuery, key: string): void {
    const registryEntry = this.waypointRegistry[key];
    if (!registryEntry.scrollEnabled) {
      // because they do not affect rendering directly, we can debounce this pretty liberally
      // but delay in case the scroll triggers a render of other elements and the top changes
      element.bind(
        'scroll.waypointEvents resize.waypointEvents',
        throttle(() => {
          $timeout(() => {
            const containerRect = element.get(0).getBoundingClientRect();
            const topThreshold = containerRect.top + registryEntry.offset;
            const waypoints = element.find('[waypoint]');
            const lastTop = this.waypointRegistry[key].top;
            const newTop = element.get(0).scrollTop;
            const inView: IViewPlacement[] = [];
            waypoints.each((_index, waypoint) => {
              const waypointRect = waypoint.getBoundingClientRect();
              if (waypointRect.bottom >= topThreshold && waypointRect.top <= containerRect.bottom) {
                inView.push({ top: waypointRect.top, elem: waypoint.getAttribute('waypoint') });
              }
            });
            this.waypointRegistry[key] = {
              lastWindow: sortBy(inView, 'top'),
              top: newTop,
              direction: lastTop > newTop ? 'up' : 'down',
            };
            if (this.waypointRegistry[key].lastWindow.length) {
              $rootScope.$broadcast('waypoints-changed', this.waypointRegistry[key]);
            }
          });
        }, 200),
      );
      registryEntry.scrollEnabled = true;
    }
  }

  public static disableWaypointEvent(key: string): void {
    const registry = this.waypointRegistry[key];
    if (registry && registry.container) {
      registry.container.unbind('scroll.waypointEvents resize.waypointEvents');
      registry.scrollEnabled = false;
      registry.container = null;
    }
  }
}
