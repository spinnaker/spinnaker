<html>
<head>
</head>
<body
  style="-ms-text-size-adjust: 100%; Margin: 0; background-color: #FAFAFA !important; box-sizing: border-box; color: #0a0a0a; font-family: Helvetica, Arial, sans-serif; font-size: 16px; font-weight: normal; line-height: 1.3; min-width: 100%; padding: 0; text-align: left; width: 100% !important;">
<table class="container" align="center"
       style="margin: 0 auto; background: #fefefe; border-collapse: collapse; border-spacing: 0; padding: 0; text-align: inherit; vertical-align: top; width: 580px;">
  <tr style="padding: 0; text-align: left; vertical-align: top;">
    <td
      style="margin: 0; background-color: #D8E8ED; border-collapse: collapse !important; color: #3A5469; font-family: Helvetica, Arial, sans-serif; font-size: 20px; font-weight: normal; height: 50px; hyphens: auto; line-height: 1.3; padding: 12px 20px; padding-left: 0; text-align: left; text-transform: uppercase; vertical-align: middle; width: 50px; word-wrap: break-word;"
      class="sp-header">
    </td>
    <td
      style="margin: 0; background-color: #D8E8ED; border-collapse: collapse !important; color: #3A5469; font-family: Helvetica, Arial, sans-serif; font-size: 20px; font-weight: normal; hyphens: auto; line-height: 1.3; padding: 0 20px; padding-left: 0; text-align: left; text-transform: uppercase; vertical-align: middle; word-wrap: break-word;"
      class="sp-header">
      Clean up Notifications
    </td>
  </tr>

  <tr style="padding: 0; text-align: left; vertical-align: top;">
    <td class="summary" colspan="2"
        style="margin: 0; background-color: #EFEFEF; border-collapse: collapse !important; color: #3A5469; font-family: Helvetica, Arial, sans-serif; font-size: 14px; font-weight: normal; hyphens: auto; line-height: 1.3; padding: 20px 20px; text-align: left; vertical-align: top; word-wrap: break-word;">
      <div class="caption" style="color: #555; font-size: 13px; margin-bottom: 8px;">SUMMARY</div>
      <strong style="color: #229CB4; font-size: 24px; font-weight: bold;">${notification.additionalContext.resources?size}</strong>
      <#if notification.additionalContext.configuration.account.name != "" && notification.additionalContext.configuration.account.name != "none">
        <span style="font-size: 16px;">${notification.additionalContext.configuration.account.name?upper_case}</span>
      </#if>

      ${notification.additionalContext.resourceType}(s) marked for clean up.
    </td>
  </tr>
  <tr style="padding: 0; text-align: left; vertical-align: top;">
    <td colspan="2" class="caption title"
        style="background-color: white; border-collapse: collapse !important; color: #555; font-family: Helvetica, Arial, sans-serif; font-size: 13px; font-weight: normal; hyphens: auto; line-height: 1.3; margin: 0 0 8px;padding: 12px 20px; text-align: left; vertical-align: top; word-wrap: break-word;">
      RESOURCES
    </td>
  </tr>


  <tr style="padding: 0; text-align: left; vertical-align: top;">
    <#foreach resource in notification.additionalContext.resources>
      <td class="item" colspan="2"
          style="margin: 0; background-color: white; border-bottom: 1px dotted #ddd; border-collapse: collapse !important; color: #0a0a0a; font-family: Helvetica, Arial, sans-serif; font-size: 14px; font-weight: normal; hyphens: auto; line-height: 1.3; padding: 8px 32px; text-align: left; vertical-align: top; word-wrap: break-word;">
        <div style="margin: 4px 0;">
          <a href="${notification.additionalContext.spinnakerLink}${resource.name}" target="_blank"
            style="margin: 0; color: #2199e8; font-family: Helvetica, Arial, sans-serif; font-weight: normal; line-height: 1.3; padding: 0; text-align: left; text-decoration: none;">
            ${resource.name}
          </a>
          <#if notification.additionalContext.configuration.location != "" && notification.additionalContext.configuration.location != "none">
            &nbsp;&nbsp;
            <span class="region"
                  style="background-color: #D6d6d6; border-radius: 2px; color: #555; padding: 4px;">
              ${notification.additionalContext.configuration.location}
            </span>
          </#if>
          <#if notification.additionalContext.configuration.account.name != "" && notification.additionalContext.configuration.account.name != "none">
             &nbsp;&nbsp;
            <span class="account"
                  style="background-color: #B92625; border-radius: 2px; color: white; font-size: 12px; font-weight: bold; padding: 4px;">
              ${notification.additionalContext.configuration.account.name?upper_case}
            </span>
          </#if>
          &nbsp;&nbsp;
        </div>
        <div class="date"
             style="margin: 8px 0;">
          <b>${resource.projectedDeletionStamp?number_to_date?string("EEE, d MMM yyyy")}</b>
          <em class="caption" style="color: #555; font-size: 13px; margin-bottom: 8px;">Deletion Scheduled</em>
        </div>
        <div class="caption small message"
             style="color: #555; font-size: 12px; margin: 14px 0 8px;">
          Reasons:
          <ul>
          <#foreach summary in resource.summaries>
            <li>${summary.description}</li>
          </#foreach>
          </ul>
        </div>
        <div class="small"
             style="font-size: 12px; margin: 4px 0;">
          <a href="${notification.additionalContext.optOutLink}${resource.name}"
            style="margin: 0; color: #2199e8; font-family: Helvetica, Arial, sans-serif; font-weight: normal; line-height: 1.3; padding: 0; text-align: left; text-decoration: none;">
            Opt out
          </a>
        </div>
      </td>
    </#foreach>
  </tr>

  <tr style="padding: 0; text-align: left; vertical-align: top;">
    <td colspan="2" class="footer"
        style="margin: 0; background-color: #F2f2f2; border-collapse: collapse !important; color: #3A5469; font-family: Helvetica, Arial, sans-serif; font-size: 14px; font-weight: normal; hyphens: auto; line-height: 1.3; padding: 30px; text-align: left; vertical-align: top; word-wrap: break-word;">
      Thanks,<br>
      Your friends from Spinnaker<br><br>
    </td>
  </tr>
</table>
</body>
</html>
