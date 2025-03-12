// Copyright (c) 2018, Snap Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package config

const defaultTemplate = `<!DOCTYPE HTML>
<html>
<head>
  <title>Spinnaker CLI</title>
  <style>
  html{font-family: Avenir Next,Helvetica Neue,Arial,Helvetica,sans-serif; -ms-text-size-adjust:100%; -webkit-text-size-adjust:100%; color:#324A5F}
  body{text-align:center; margin: 10% auto; background: #D1E4EA; overflow: hidden; position: relative; width: 100%; height: 100vh; min-height: 480px; z-index: 1; }
  .logo { display:block; background-size: 106px 100px; width: 106px; height: 100px; margin: 0px auto; }
  h1 { width: 100%; text-align: center; }
  p { padding: 10px; width: 100%; text-align: center; }
</style></head><body>
<div class="container"><h1>{{.header}}</h1><p>{{range $msg := .messages}}{{$msg}}<br/>{{end}}<br><br><b>Continue actions in terminal</b></p>
</body></html>`
