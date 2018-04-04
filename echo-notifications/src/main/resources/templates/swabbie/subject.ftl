Clean up Notification for ${notification.additionalContext.resourceOwner}
<#if notification.additionalContext.configuration.account.name != "" && notification.additionalContext.configuration.account.name != "none">
  in ${notification.additionalContext.configuration.account.name?upper_case}
</#if>
