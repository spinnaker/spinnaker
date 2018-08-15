[Cleanup Notification] ${notification.additionalContext.resourceType}s scheduled for deletion for ${notification.additionalContext.resourceOwner}
<#if notification.additionalContext.configuration.account.name != "" && notification.additionalContext.configuration.account.name != "none">
  in ${notification.additionalContext.configuration.account.name?upper_case}
</#if>
<#if notification.additionalContext.configuration.location != "" && notification.additionalContext.configuration.location != "none">
 | ${notification.additionalContext.configuration.location?upper_case}
</#if>
