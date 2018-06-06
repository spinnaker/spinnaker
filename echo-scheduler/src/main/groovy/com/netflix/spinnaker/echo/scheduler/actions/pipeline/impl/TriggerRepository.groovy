package com.netflix.spinnaker.echo.scheduler.actions.pipeline.impl

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

        Trigger previous = triggersById.put(trigger.id, trigger)
        if (previous) {
          log.warn("Duplicate trigger ids found: ${previous} with parent ${previous.parent} and ${trigger} with parent ${trigger.parent}")
        }
      }
    }
  }

  // visible for testing
  static String extractTriggerId(String id) {
    int index = id.lastIndexOf(':')
    return (index == -1) ? id : id.substring(index + 1)
  }

  /**
   * @param id can be a straight-up trigger id or an ActionInstance composite id of the form:
   *           composites are of the form <pipeId>:className:<triggerId>, so in this case we want to extract <triggerId>
   *           for the lookup
   * @return null if no trigger is mapped to id
   */
  @Nullable
  public Trigger getTrigger(String id) {
    if (id == null) {
      return null
    }

    return triggersById.get(id) ?: triggersById.get(extractTriggerId(id))
  }

  public Trigger remove(String id) {
    if (id == null) {
      return null
    }

    return triggersById.remove(id) ?: triggersById.remove(extractTriggerId(id))
  }

  public Collection<Trigger> triggers() {
    return triggersById.values()
  }
}
