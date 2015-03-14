package com.netflix.spinnaker.orca.pipeline.util

import org.apache.commons.lang.text.StrSubstitutor

/**
 * Common methods for dealing with passing context parameters used by both Script and Jenkins stages
 * @author clin
 */
class ContextParameterProcessor {

  static Map process(Map parameters, Map context) {
    if(!parameters){
      return null
    }

    Map flattenedMap = flattenMap('', context)
    StrSubstitutor substitutor = new StrSubstitutor(flattenedMap)
    parameters.collectEntries { k, v ->
      [k, substitutor.replace(v)]
    }
  }

  static Map flattenMap(String prefix, Object o) {
    Map result = [:]
    if (o instanceof Map) {
      o.each { k, v ->
        result = result + flattenMap("${prefix.empty ? '' : (prefix + '.')}${k}", v)
      }
    } else {
      result[prefix] = o
    }
    result
  }

}
