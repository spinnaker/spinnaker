package com.netflix.spinnaker.clouddriver.deploy;

import com.netflix.spinnaker.kork.web.exceptions.ValidationException;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.validation.Errors;

public class DescriptionValidationException extends ValidationException {
  public DescriptionValidationException(Errors errors) {
    super("Validation Failed", getErrors(errors));
  }

  public DescriptionValidationException(Collection<String> errors) {
    super("Validation Failed", errors);
  }

  public static Collection<String> getErrors(Errors errors) {

    return errors.getAllErrors().stream()
        .map(
            objectError ->
                Optional.ofNullable(objectError.getDefaultMessage()).orElse(objectError.getCode()))
        .collect(Collectors.toList());
  }
}
