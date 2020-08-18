# \AuthControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetServiceAccountsUsingGET**](AuthControllerApi.md#GetServiceAccountsUsingGET) | **Get** /auth/user/serviceAccounts | Get service accounts
[**LoggedOutUsingGET**](AuthControllerApi.md#LoggedOutUsingGET) | **Get** /auth/loggedOut | Get logged out message
[**RedirectUsingGET**](AuthControllerApi.md#RedirectUsingGET) | **Get** /auth/redirect | Redirect to Deck
[**SyncUsingPOST**](AuthControllerApi.md#SyncUsingPOST) | **Post** /auth/roles/sync | Sync user roles
[**UserUsingGET**](AuthControllerApi.md#UserUsingGET) | **Get** /auth/user | Get user


# **GetServiceAccountsUsingGET**
> []interface{} GetServiceAccountsUsingGET(ctx, optional)
Get service accounts

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
 **optional** | ***AuthControllerApiGetServiceAccountsUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a AuthControllerApiGetServiceAccountsUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **application** | **optional.String**| application | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **LoggedOutUsingGET**
> string LoggedOutUsingGET(ctx, )
Get logged out message

### Required Parameters
This endpoint does not need any parameter.

### Return type

**string**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **RedirectUsingGET**
> RedirectUsingGET(ctx, to)
Redirect to Deck

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **to** | **string**| to | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **SyncUsingPOST**
> SyncUsingPOST(ctx, )
Sync user roles

### Required Parameters
This endpoint does not need any parameter.

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **UserUsingGET**
> User UserUsingGET(ctx, )
Get user

### Required Parameters
This endpoint does not need any parameter.

### Return type

[**User**](User.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

