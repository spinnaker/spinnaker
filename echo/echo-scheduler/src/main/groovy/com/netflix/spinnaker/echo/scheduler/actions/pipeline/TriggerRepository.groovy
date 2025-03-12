package com.netflix.spinnaker.echo.scheduler.actions.pipeline

import com.netflix.spinnaker.echo.model.Pipeline
import com.netflix.spinnaker.echo.model.Trigger
import groovy.util.logging.Slf4j

import javax.annotation.Nullable

@Slf4j
class TriggerRepository {
  private final Map<String, Trigger> triggersById

  TriggerRepository(List<Pipeline> pipelines) {
    triggersById = new HashMap<>()
    for (Pipeline pipeline : pipelines) {
      for (Trigger trigger : pipeline.triggers) {
        if (trigger.id == null) {
          // see PipelineCache::decorateTriggers
          throw new IllegalStateException("Trigger with null id ${trigger}")
        }

        if ((Trigger.Type.CRON.toString().equalsIgnoreCase(trigger.type)) &&
          trigger.enabled) {
          Trigger previous = triggersById.put(trigger.id, trigger)
          if (previous) {
            log.warn("Duplicate trigger ids found: ${previous} with parent ${previous.parent} and ${trigger} with parent ${trigger.parent}")
          }
        }
      }
    }
  }

  /**
   * @param id
   * @return null if no trigger is mapped to id
   */
  @Nullable
  Trigger getTrigger(String id) {
    if (id == null) {
      return null
    }

    return triggersById.get(id)
  }

  Trigger remove(String id) {
    if (id == null) {
      return null
    }

    return triggersById.remove(id)
  }

  Collection<Trigger> triggers() {
    return triggersById.values()
  }
}
