<p>The following applications have been reassigned to you as their owner is no longer a Netflix employee and you are the current manager of their team.</p>
<#foreach event in notification.additionalContext.events>
${event.user.name} (${event.user.email}) owned...
<ul>
  <#foreach application in event.applications><li>${application.name}</li></#foreach>
</ul>
<br/>
</#foreach>
