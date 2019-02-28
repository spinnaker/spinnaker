# \ArtifactControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**AllUsingGET**](ArtifactControllerApi.md#AllUsingGET) | **Get** /artifacts/credentials | Retrieve the list of artifact accounts configured in Clouddriver.
[**ArtifactVersionsUsingGET**](ArtifactControllerApi.md#ArtifactVersionsUsingGET) | **Get** /artifacts/account/{accountName}/versions | Retrieve the list of artifact versions by account and artifact names


# **AllUsingGET**
> []interface{} AllUsingGET(ctx, optional)
Retrieve the list of artifact accounts configured in Clouddriver.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ArtifactVersionsUsingGET**
> []string ArtifactVersionsUsingGET(ctx, accountName, type_, artifactName, optional)
Retrieve the list of artifact versions by account and artifact names

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **accountName** | **string**| accountName | 
  **type_** | **string**| type | 
  **artifactName** | **string**| artifactName | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **accountName** | **string**| accountName | 
 **type_** | **string**| type | 
 **artifactName** | **string**| artifactName | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

**[]string**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

