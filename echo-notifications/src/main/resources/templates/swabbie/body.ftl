<!DOCTYPE html>
<html>
<head>
  <title>Cleanup Notifications</title>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta http-equiv="X-UA-Compatible" content="IE=edge" />
  <style type="text/css">
    /* CLIENT-SPECIFIC STYLES */
    body, table, td, a{-webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%;} /* Prevent WebKit and Windows mobile changing default text sizes */
    table, td{mso-table-lspace: 0pt; mso-table-rspace: 0pt;} /* Remove spacing between tables in Outlook 2007 and up */
    img{-ms-interpolation-mode: bicubic;} /* Allow smoother rendering of resized image in Internet Explorer */
    /* RESET STYLES */
    img{border: 0; height: auto; line-height: 100%; outline: none; text-decoration: none;}
    table{border-collapse: collapse !important;}
    body{height: 100% !important; margin: 0 !important; padding: 0 !important; width: 100% !important;}
    /* iOS BLUE LINKS */
    a[x-apple-data-detectors] {
      color: inherit !important;
      text-decoration: none !important;
      font-size: inherit !important;
      font-family: inherit !important;
      font-weight: inherit !important;
      line-height: inherit !important;
    }
    /* MOBILE STYLES */
    @media screen and (max-width: 600px) {
      .header td,
      .loop__data td,
      .loop td {
        display: block !important;
        box-sizing: border-box;
        clear: both;
      }
      .m-logo {
        display: block !important;
      }
      td.logo {
        display: none !important;
      }
      .title {
        padding: 0 12px 16px !important;
      }
      .title h1 {
        font-size: 18px !important;
      }
      .title h2 {
        font-size: 14px !important;
      }
      .middle {
        padding: 32px 4px !important;
      }
      .loop__data {
        margin: 8px;
        width: auto !important;
      }
      td.loop__number {
        display: none !important;
      }
      .loop__key {
        text-align: left !important;
        width: auto !important;
        padding-top: 8px !important;
        padding-bottom: 0px !important;
      }
      .loop__btns {
        width: 100% !important;
        padding: 0 8px 12px 8px !important;
        text-align: right;
      }
      .loop__btns a {
        display: inline-block !important;
        margin: 0 4px !important;
      }
    }
    /* ANDROID CENTER FIX */
    div[style*="margin: 16px 0;"] { margin: 0 !important; }
  </style>

</head>
<body style="margin: 0 !important; padding: 0 !important;">
<table border="0" cellpadding="0" cellspacing="0" width="100%">
  <tr>
    <!-- HEADER -->
    <td bgcolor="#333333" style="border-bottom: 3px solid #129cb5;" align="center">
      <table border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 1000px;" class="header">
        <tr>
          <td align="center" valign="top" style="padding: 16px 12px 0 12px; display: none;" class="m-logo">
            <img alt="Spinnaker" src="https://www.spinnaker.io/assets/emails/spinnaker-logo-400.png" width="100" height="100" border="0">
          </td>
          <td class="title" align="left" valign="middle" style="padding: 16px 0 16px 32px; height: 100%;">
            <h1 style="display: block; font-family: Helvetica, Arial, sans-serif; color: #ffffff; font-size: 28px;">Cleanup Notifications</h1>
            <h2 style="display: block; font-family: Helvetica, Arial, sans-serif; color: #d8d8d8; font-size: 16px; font-weight: normal;">${notification.additionalContext.resources?size} resource(s) not in use scheduled for cleanup</h2>
          </td>
          <td align="center" valign="top" style="padding: 16px 32px 16px 16px; width: 140px;" class="logo">
            <img alt="Spinnaker" src="https://www.spinnaker.io/assets/emails/spinnaker-logo-400.png" width="140" height="140" border="0">
          </td>
        </tr>
      </table>
    </td>
  </tr>

  <tr>
    <!-- MIDDLE -->
    <td class="middle" bgcolor="#d7e8ed" align="center" style="padding: 32px 16px;">

      <!-- CONTENT -->
      <table bgcolor="#ffffff" border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 1000px;">
        <#assign notificationContext = notification.additionalContext>
        <!-- LOOP -->
        <#foreach resourceData in notificationContext.resources>
          <tr class="loop" style="border-bottom: 1px solid #cccccc;">
            <td class="loop__number" valign="middle" style="padding: 16px 16px 16px 32px; width: 40px; font-family: Helvetica, Arial, sans-serif; color: #cccccc; font-size: 18px; font-weight: bold;">
              ${resourceData?index + 1}
            </td>
            <td>
              <table class="loop__data" border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 1000px;" class="wrapper">
                <tr>
                  <td class="loop__key" align="right" valign="top" style="padding: 4px 16px 4px 0; font-family: Helvetica, Arial, sans-serif; font-size: 12px; font-weight: bold;" width="30%">
                    ${notificationContext.resourceType}
                  </td>

                  <td align="left" style="padding: 4px 0; font-family: Helvetica, Arial, sans-serif; font-size: 12px;">
                    <a href="${resourceData.resourceUrl}">${resourceData.resource.resourceId}</a>
                  </td>
                </tr>
                <#if resourceData.resource.resourceId != resourceData.resource.name>
                  <tr>
                    <td class="loop__key" align="right" valign="top" style="padding: 4px 16px 4px 0; font-family: Helvetica, Arial, sans-serif; font-size: 12px; font-weight: bold;" width="30%">
                      name
                    </td>
                    <td align="left" style="padding: 4px 0; font-family: Helvetica, Arial, sans-serif; font-size: 12px;">
                      ${resourceData.resource.name}
                    </td>
                  </tr>
                </#if>
                <tr>
                  <td class="loop__key" align="right" valign="top" style="padding: 4px 16px 4px 0; font-family: Helvetica, Arial, sans-serif; font-size: 12px; font-weight: bold;" width="30%">
                    account
                  </td>
                  <td align="left" style="padding: 4px 0; font-family: Helvetica, Arial, sans-serif; font-size: 12px;">
                    ${resourceData.account}
                  </td>
                </tr>
                <tr>
                  <td class="loop__key" align="right" valign="top" style="padding: 4px 16px 4px 0; font-family: Helvetica, Arial, sans-serif; font-size: 12px; font-weight: bold;" width="30%">
                    region
                  </td>
                  <td align="left" style="padding: 4px 0; font-family: Helvetica, Arial, sans-serif; font-size: 12px;">
                    ${resourceData.location}
                  </td>
                </tr>
                <#if resourceData.resource.createTs??>
                  <tr>
                    <td class="loop__key" align="right" valign="top" style="padding: 4px 16px 4px 0; font-family: Helvetica, Arial, sans-serif; font-size: 12px; font-weight: bold;" width="30%">
                      created on
                    </td>
                    <td align="left" style="padding: 4px 0; font-family: Helvetica, Arial, sans-serif; font-size: 12px;">
                      ${resourceData.resource.createTs?number_to_date?string("EEE, d MMM yyyy")}
                    </td>
                  </tr>
                </#if>
                <#if resourceData.resource.lastSeenInfo??>
                  <tr>
                    <td class="loop__key" align="right" valign="top" style="padding: 4px 16px 4px 0; font-family: Helvetica, Arial, sans-serif; font-size: 12px; font-weight: bold;" width="30%">
                      last seen
                    </td>
                    <td align="left" style="padding: 4px 0; font-family: Helvetica, Arial, sans-serif; font-size: 12px;">
                      ${resourceData.resource.lastSeenInfo.timeSeen?number_to_date?string("EEE, d MMM yyyy")} - <span style="color: #999999;">${resourceData.resource.lastSeenInfo.usedByResourceIdentifier!""}</span>
                    </td>
                  </tr>
                </#if>
                <tr>
                  <td class="loop__key" align="right" valign="top" style="padding: 4px 16px 4px 0; font-family: Helvetica, Arial, sans-serif; font-size: 12px; font-weight: bold;" width="30%">
                    cleanup date
                  </td>
                  <td align="left" style="padding: 4px 0; font-family: Helvetica, Arial, sans-serif; font-size: 12px;">
                    ${resourceData.deletionDate}
                  </td>
                </tr>
              </table>
            </td>
            <td class="loop__btns" valign="middle" style="padding: 16px 32px 16px 16px; width: 145px;">
              <a href="${resourceData.optOutUrl}" target="_blank" style="font-size: 14px; font-family: Helvetica, Arial, sans-serif; color: #666666; margin: 8px 0; text-decoration: none; background-color: #ffffff; text-align: center; text-decoration: none; border-radius: 4px; padding: 8px 16px; display: block; border: 1px solid #cccccc" class="mobile-button">Opt Out of Delete</a>
            </td>
          </tr>
        </#foreach>

      </table>
    </td>
  </tr>
  <tr>
    <td bgcolor="#ffffff" style="border-top: 3px solid #737373; padding: 32px 16px 96px 16px; font-size: 18px; font-family: Helvetica, Arial, sans-serif; color: #737373;" align="center">
      For questions, reach out to <a href="${notificationContext.slackChannelLink}" target="_blank" >#spinnaker</a> in Slack.
    </td>
  </tr>
</table>
</body>
</html>
