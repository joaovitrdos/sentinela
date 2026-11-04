$ErrorActionPreference = 'Stop'

function New-RequestItem {
    param(
        [string] $Name,
        [string] $Method,
        [string] $Path,
        [string] $Description = '',
        [string] $Body = $null,
        [switch] $Public,
        [switch] $SaveAccessToken,
        [switch] $ClearAccessToken
    )

    $request = [ordered]@{
        method = $Method
        header = @()
        url = '{{baseUrl}}' + $Path
        description = $Description
    }
    if ($Body) {
        $request.header = @(@{ key = 'Content-Type'; value = 'application/json'; type = 'text' })
        $request.body = @{ mode = 'raw'; raw = $Body; options = @{ raw = @{ language = 'json' } } }
    }
    if ($Public) { $request.auth = @{ type = 'noauth' } }

    $item = [ordered]@{ name = $Name; request = $request }
    if ($SaveAccessToken) {
        $item.event = @(@{
            listen = 'test'
            script = @{
                type = 'text/javascript'
                exec = @(
                    'if (pm.response.code >= 200 && pm.response.code < 300) {'
                    '  const token = pm.response.json().accessToken;'
                    '  if (token) pm.environment.set("accessToken", token);'
                    '}'
                )
            }
        })
    }
    if ($ClearAccessToken) {
        $item.event = @(@{
            listen = 'test'
            script = @{
                type = 'text/javascript'
                exec = @('if (pm.response.code >= 200 && pm.response.code < 300) pm.environment.unset("accessToken");')
            }
        })
    }
    return $item
}

$authRequests = @(
    (New-RequestItem 'Register' 'POST' '/auth/register' 'Creates an account and sends an email confirmation code.' '{"name":"{{name}}","email":"{{email}}","password":"{{password}}"}' -Public),
    (New-RequestItem 'Confirm email' 'POST' '/auth/email/confirm' 'Use the eight-digit code sent to the email address.' '{"code":"{{verificationCode}}","email":"{{email}}"}' -Public -SaveAccessToken),
    (New-RequestItem 'Resend confirmation' 'POST' '/auth/email/resend' '' '{"email":"{{email}}"}' -Public),
    (New-RequestItem 'Login' 'POST' '/auth/login' 'Salva accessToken no ambiente após login bem-sucedido.' '{"email":"{{email}}","password":"{{password}}"}' -Public -SaveAccessToken),
    (New-RequestItem 'Google login' 'POST' '/auth/google' 'Use the ID token issued by Google.' '{"idToken":"{{googleIdToken}}"}' -Public -SaveAccessToken),
    (New-RequestItem 'Logout' 'POST' '/auth/logout' 'Ends the current token session.' $null -ClearAccessToken),
    (New-RequestItem 'Logout all devices' 'POST' '/auth/logout-all' 'Ends all sessions for the authenticated user.' $null -ClearAccessToken),
    (New-RequestItem 'Request password recovery' 'POST' '/auth/password/forgot' '' '{"email":"{{email}}"}' -Public),
    (New-RequestItem 'Validate recovery code' 'POST' '/auth/password/validate' '' '{"code":"{{recoveryCode}}","email":"{{email}}"}' -Public),
    (New-RequestItem 'Reset password' 'POST' '/auth/password/reset' '' '{"code":"{{recoveryCode}}","email":"{{email}}","newPassword":"{{newPassword}}"}' -Public)
)

$userRequests = @(
    (New-RequestItem 'My profile' 'GET' '/users/me' 'Requires accessToken.'),
    (New-RequestItem 'Update profile' 'PUT' '/users/me' '' '{"name":"{{name}}","picture":"{{picture}}"}'),
    (New-RequestItem 'Delete my account' 'DELETE' '/users/me' 'Irreversible: deletes the authenticated account.'),
    (New-RequestItem 'Request email change' 'POST' '/users/me/email' '' '{"newEmail":"{{newEmail}}","currentPassword":"{{currentPassword}}"}'),
    (New-RequestItem 'Confirm email change' 'POST' '/users/me/email/confirm' '' '{"code":"{{verificationCode}}"}'),
    (New-RequestItem 'Change password' 'POST' '/users/change-password' '' '{"currentPassword":"{{currentPassword}}","newPassword":"{{newPassword}}"}'),
    (New-RequestItem 'My profile by ID' 'GET' '/users/{{userId}}' 'Requires accessToken. Set userId to the authenticated user UUID; other IDs return 404.'),
    (New-RequestItem 'Count users' 'GET' '/users/count' 'Requires accessToken.')
)

$hydroRequests = @(
    (New-RequestItem 'Rainfall in the last 24 hours' 'GET' '/api/hydro/rain/24h'),
    (New-RequestItem 'River levels' 'GET' '/api/hydro/levels'),
    (New-RequestItem 'Hydrological stations' 'GET' '/api/hydro/stations'),
    (New-RequestItem 'River network' 'GET' '/api/hydro/rivers')
)

$weatherRequests = @(
    (New-RequestItem 'Current weather by coordinates' 'GET' '/api/weather/current?latitude={{latitude}}&longitude={{longitude}}'),
    (New-RequestItem 'Forecast by coordinates' 'GET' '/api/weather/forecast?latitude={{latitude}}&longitude={{longitude}}&days={{forecastDays}}'),
    (New-RequestItem 'Time zone by coordinates' 'GET' '/api/weather/time-zone?latitude={{latitude}}&longitude={{longitude}}')
)

$collection = [ordered]@{
    info = @{
        _postman_id = [guid]::NewGuid().ToString()
        name = 'Sentinela API'
        description = 'Sentinela backend endpoints. Select the Sentinela Local environment and update values in one place. Log in or confirm an email to set accessToken automatically.'
        schema = 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json'
    }
    auth = @{ type = 'bearer'; bearer = @(@{ key = 'token'; value = '{{accessToken}}'; type = 'string' }) }
    item = @(
        @{ name = 'Authentication'; item = $authRequests },
        @{ name = 'Users'; item = $userRequests },
        @{ name = 'Hydrology'; item = $hydroRequests },
        @{ name = 'Weather'; item = $weatherRequests }
    )
}

$environment = [ordered]@{
    id = [guid]::NewGuid().ToString()
    name = 'Sentinela Local'
    values = @(
        @{ key = 'baseUrl'; value = 'http://localhost:9090'; type = 'default'; enabled = $true },
        @{ key = 'accessToken'; value = ''; type = 'secret'; enabled = $true },
        @{ key = 'name'; value = 'Nome de teste'; type = 'default'; enabled = $true },
        @{ key = 'email'; value = 'usuario@exemplo.com'; type = 'default'; enabled = $true },
        @{ key = 'password'; value = 'TroqueEstaSenha123!'; type = 'secret'; enabled = $true },
        @{ key = 'currentPassword'; value = 'TroqueEstaSenha123!'; type = 'secret'; enabled = $true },
        @{ key = 'newPassword'; value = 'NovaSenha123!'; type = 'secret'; enabled = $true },
        @{ key = 'newEmail'; value = 'novo-email@exemplo.com'; type = 'default'; enabled = $true },
        @{ key = 'verificationCode'; value = '00000000'; type = 'secret'; enabled = $true },
        @{ key = 'recoveryCode'; value = '00000000'; type = 'secret'; enabled = $true },
        @{ key = 'googleIdToken'; value = ''; type = 'secret'; enabled = $true },
        @{ key = 'userId'; value = '00000000-0000-0000-0000-000000000000'; type = 'default'; enabled = $true },
        @{ key = 'picture'; value = 'https://exemplo.com/avatar.png'; type = 'default'; enabled = $true },
        @{ key = 'latitude'; value = '-19.539'; type = 'default'; enabled = $true },
        @{ key = 'longitude'; value = '-40.630'; type = 'default'; enabled = $true },
        @{ key = 'forecastDays'; value = '3'; type = 'default'; enabled = $true }
    )
    _postman_variable_scope = 'environment'
    _postman_exported_at = (Get-Date).ToUniversalTime().ToString('o')
    _postman_exported_using = 'Postman/12'
}

$collectionPath = Join-Path $PSScriptRoot 'Sentinela API.postman_collection.json'
$environmentPath = Join-Path $PSScriptRoot 'Sentinela Local.postman_environment.json'
$collection | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $collectionPath -Encoding utf8
$environment | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $environmentPath -Encoding utf8
Write-Output "Coleção: $collectionPath"
Write-Output "Ambiente: $environmentPath"
