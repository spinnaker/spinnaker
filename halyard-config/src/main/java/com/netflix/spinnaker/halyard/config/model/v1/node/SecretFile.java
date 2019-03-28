package com.netflix.spinnaker.halyard.config.model.v1.node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotates a field that points to a file that contains one or more secrets.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SecretFile {
  String prefix() default "";
}
