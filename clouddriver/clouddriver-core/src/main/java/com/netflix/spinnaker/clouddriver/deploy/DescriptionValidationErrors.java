package com.netflix.spinnaker.clouddriver.deploy;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.SneakyThrows;
import org.springframework.validation.AbstractErrors;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

public class DescriptionValidationErrors extends AbstractErrors implements ValidationErrors {
  private Object description;
  private List<ObjectError> globalErrors = new ArrayList<>();
  private List<FieldError> fieldErrors = new ArrayList<>();

  public DescriptionValidationErrors(Object description) {
    this.description = description;
  }

  @Nonnull
  @Override
  public String getObjectName() {
    return description.getClass().getSimpleName();
  }

  @Override
  public void reject(@Nonnull String errorCode, Object[] errorArgs, String defaultMessage) {
    globalErrors.add(
        new ObjectError(getObjectName(), new String[] {errorCode}, errorArgs, defaultMessage));
  }

  @Override
  public void rejectValue(
      String field, @Nonnull String errorCode, Object[] errorArgs, String defaultMessage) {
    fieldErrors.add(
        new FieldError(
            getObjectName(),
            field,
            null,
            false,
            new String[] {errorCode},
            errorArgs,
            defaultMessage));
  }

  @Override
  public void addAllErrors(Errors errors) {
    globalErrors.addAll(errors.getAllErrors());
  }

  @Override
  @SneakyThrows
  public Object getFieldValue(@Nonnull String field) {
    return description.getClass().getDeclaredField(field).get(description);
  }

  public Object getDescription() {
    return description;
  }

  public void setDescription(Object description) {
    this.description = description;
  }

  @Nonnull
  public List<ObjectError> getGlobalErrors() {
    return globalErrors;
  }

  public void setGlobalErrors(List<ObjectError> globalErrors) {
    this.globalErrors = globalErrors;
  }

  @Nonnull
  public List<FieldError> getFieldErrors() {
    return fieldErrors;
  }

  public void setFieldErrors(List<FieldError> fieldErrors) {
    this.fieldErrors = fieldErrors;
  }
}
