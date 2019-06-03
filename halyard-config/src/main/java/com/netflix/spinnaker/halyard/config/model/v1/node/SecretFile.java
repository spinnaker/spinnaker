package com.netflix.spinnaker.halyard.config.model.v1.node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** This annotates a field that points to a file that contains one or more secrets. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SecretFile {
  String prefix() default "";
  /**
   * @return true to force a secret to be decrypted even when the service it is attached to supports
   *     decryption. This is true for properties that cannot be decrypted at runtime (e.g.
   *     spinnaker-monitoring settings).
   */
  boolean alwaysDecrypt() default false;
}
