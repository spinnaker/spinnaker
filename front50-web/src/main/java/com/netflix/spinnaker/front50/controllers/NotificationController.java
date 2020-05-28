package com.netflix.spinnaker.front50.controllers;

import static java.lang.String.format;

import com.google.common.base.Strings;
import com.netflix.spinnaker.front50.UntypedUtils;
import com.netflix.spinnaker.front50.exception.NotFoundException;
import com.netflix.spinnaker.front50.model.notification.HierarchicalLevel;
import com.netflix.spinnaker.front50.model.notification.Notification;
import com.netflix.spinnaker.front50.model.notification.NotificationDAO;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Controller for presets */
@RestController
@RequestMapping("notifications")
public class NotificationController {

  private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

  private final NotificationDAO notificationDAO;

  public NotificationController(NotificationDAO notificationDAO) {
    this.notificationDAO = notificationDAO;
  }

  @RequestMapping(value = "", method = RequestMethod.GET)
  public Collection<Notification> list() {
    return notificationDAO.all();
  }

  @RequestMapping(value = "global", method = RequestMethod.GET)
  public Notification getGlobal() {
    return notificationDAO.getGlobal();
  }

  @RequestMapping(value = "global", method = RequestMethod.POST)
  public void saveGlobal(@RequestBody Notification notification) {
    notificationDAO.saveGlobal(notification);
  }

  @PostAuthorize("hasPermission(#name, 'APPLICATION', 'READ')")
  @RequestMapping(value = "{type}/{name}", method = RequestMethod.GET)
  public Notification listByApplication(
      @PathVariable(value = "type") String type, @PathVariable(value = "name") String name) {
    HierarchicalLevel level = getLevel(type);
    final Notification notification = notificationDAO.get(level, name);

    if (level.equals(HierarchicalLevel.APPLICATION)) {
      final Object global = getGlobal();

      NotificationDAO.NOTIFICATION_FORMATS.forEach(
          it -> {
            if (UntypedUtils.hasProperty(global, it)) {
              if (!UntypedUtils.hasProperty(notification, it)) {
                UntypedUtils.setProperty(notification, it, new ArrayList<>());
              }

              ((List) UntypedUtils.getProperty(notification, it))
                  .addAll((List) UntypedUtils.getProperty(global, it));
            }
          });
    }

    return notification;
  }

  @RequestMapping(value = "batchUpdate", method = RequestMethod.POST)
  public void batchUpdate(@RequestBody List<Notification> notifications) {
    notifications.forEach(
        it -> {
          try {
            save("application", (String) it.get("application"), it);
            log.info("inserted {}", it);
          } catch (Exception e) {
            log.error("could not insert {}", it, e);
          }
        });
  }

  @PreAuthorize(
      "@fiatPermissionEvaluator.storeWholePermission() and hasPermission(#name, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "{type}/{name}", method = RequestMethod.POST)
  public void save(
      @PathVariable(value = "type") String type,
      @PathVariable(value = "name") String name,
      @RequestBody Notification notification) {
    if (!Strings.isNullOrEmpty(name)) {
      notificationDAO.save(getLevel(type), name, notification);
    }
  }

  @PreAuthorize(
      "@fiatPermissionEvaluator.storeWholePermission() and hasPermission(#name, 'APPLICATION', 'WRITE')")
  @RequestMapping(value = "{type}/{name}", method = RequestMethod.DELETE)
  public void delete(
      @PathVariable(value = "type") String type, @PathVariable(value = "name") String name) {
    HierarchicalLevel level = getLevel(type);
    notificationDAO.delete(level, name);
  }

  private static HierarchicalLevel getLevel(final String type) {
    HierarchicalLevel result = HierarchicalLevel.fromString(type);
    if (result == null) {
      throw new NotFoundException(format("No hierarchical level matches '%s'", type));
    }
    return result;
  }
}
