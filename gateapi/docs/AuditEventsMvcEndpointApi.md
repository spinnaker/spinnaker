# \AuditEventsMvcEndpointApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**FindByPrincipalAndAfterAndTypeUsingGET**](AuditEventsMvcEndpointApi.md#FindByPrincipalAndAfterAndTypeUsingGET) | **Get** /auditevents | findByPrincipalAndAfterAndType
[**FindByPrincipalAndAfterAndTypeUsingGET1**](AuditEventsMvcEndpointApi.md#FindByPrincipalAndAfterAndTypeUsingGET1) | **Get** /auditevents.json | findByPrincipalAndAfterAndType


# **FindByPrincipalAndAfterAndTypeUsingGET**
> interface{} FindByPrincipalAndAfterAndTypeUsingGET(ctx, optional)
findByPrincipalAndAfterAndType

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **principal** | **string**| principal | 
 **after** | **time.Time**| after | 
 **type_** | **string**| type | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/vnd.spring-boot.actuator.v1+json, application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **FindByPrincipalAndAfterAndTypeUsingGET1**
> interface{} FindByPrincipalAndAfterAndTypeUsingGET1(ctx, optional)
findByPrincipalAndAfterAndType

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **principal** | **string**| principal | 
 **after** | **time.Time**| after | 
 **type_** | **string**| type | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/vnd.spring-boot.actuator.v1+json, application/json

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

