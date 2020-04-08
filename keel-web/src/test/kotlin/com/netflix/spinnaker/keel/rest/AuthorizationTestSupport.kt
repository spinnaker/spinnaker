package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

/**
 * Mocks authorization to pass for any and all API calls.
 */
fun AuthorizationSupport.allowAll() {
  every {
    hasApplicationPermission(any<String>(), any(), any())
  } returns true
  every {
    checkApplicationPermission(any<Action>(), any(), any())
  } just Runs
  every {
    hasServiceAccountAccess(any<String>(), any())
  } returns true
  every {
    hasServiceAccountAccess(any())
  } returns true
  every {
    checkServiceAccountAccess(any<TargetEntity>(), any())
  } just Runs
  every {
    hasCloudAccountPermission(any<String>(), any(), any())
  } returns true
  every {
    checkCloudAccountPermission(any<Action>(), any(), any())
  } just Runs
}

/**
 * Mocks authorization to pass for [AuthorizationSupport.hasApplicationPermission].
 */
fun AuthorizationSupport.allowApplicationAccess(action: Action, target: TargetEntity) {
  every {
    hasApplicationPermission(action.name, target.name, any())
  } returns true
  every {
    checkApplicationPermission(action, target, any())
  } just Runs
}

/**
 * Mocks authorization to fail for [AuthorizationSupport.hasApplicationPermission].
 */
fun AuthorizationSupport.denyApplicationAccess(action: Action, target: TargetEntity) {
  every {
    hasApplicationPermission(action.name, target.name, any())
  } returns false
  every {
    checkApplicationPermission(action, target, any())
  } throws AccessDeniedException("Nuh-uh!")
}

/**
 * Mocks authorization to pass for [AuthorizationSupport.hasCloudAccountPermission].
 */
fun AuthorizationSupport.allowCloudAccountAccess(action: Action, target: TargetEntity) {
  every {
    hasCloudAccountPermission(action.name, target.name, any())
  } returns true
  every {
    checkCloudAccountPermission(action, target, any())
  } just Runs
}

/**
 * Mocks authorization to fail for [AuthorizationSupport.hasCloudAccountPermission].
 */
fun AuthorizationSupport.denyCloudAccountAccess(action: Action, target: TargetEntity) {
  every {
    hasCloudAccountPermission(action.name, target.name, any())
  } returns false
  every {
    checkCloudAccountPermission(action, target, any())
  } throws AccessDeniedException("Nuh-uh!")
}

/**
 * Mocks authorization to pass for [AuthorizationSupport.hasServiceAccountAccess].
 */
fun AuthorizationSupport.allowServiceAccountAccess() {
  every {
    hasServiceAccountAccess(any(), any())
  } returns true
  every {
    checkServiceAccountAccess(any(), any())
  } just Runs
}

/**
 * Mocks authorization to fail for [AuthorizationSupport.hasServiceAccountAccess].
 */
fun AuthorizationSupport.denyServiceAccountAccess() {
  every {
    hasServiceAccountAccess(any(), any())
  } returns false
  every {
    hasServiceAccountAccess(any())
  } returns false
  every {
    checkServiceAccountAccess(any(), any())
  } throws AccessDeniedException("Nuh-uh!")
}

fun MockHttpServletRequestBuilder.addData(jsonMapper: ObjectMapper, data: Any?): MockHttpServletRequestBuilder =
  if (data != null) {
    content(jsonMapper.writeValueAsString(data))
      .contentType(MediaType.APPLICATION_JSON)
  } else {
    this
  }
