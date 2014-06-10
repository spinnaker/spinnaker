package com.netflix.spinnaker.oort.ext;

import groovy.lang.Closure;
import groovy.transform.CompileStatic;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.util.LinkedHashMap;
import java.util.Map;

@CompileStatic
public class MapExtensions {
  public static <T extends Map> T specialSubtract(T mapA, final T mapB) {
    return ((T) (DefaultGroovyMethods.collectEntries(mapA, new Closure<LinkedHashMap<Object, Object>>(null, null) {
      public LinkedHashMap<Object, Object> doCall(Object key, Object val) {
        if (mapB.containsKey(key) && !mapB.get(key).equals(val)) {
          LinkedHashMap<Object, Object> map = new LinkedHashMap<Object, Object>(1);
          map.put(key, mapB.get(key));
          return map;
        } else if (!mapB.containsKey(key)) {
          LinkedHashMap<Object, Object> map = new LinkedHashMap<Object, Object>(1);
          map.put(key, val);
          return map;
        } else {
          return new LinkedHashMap();
        }

      }

    })));
  }

}
