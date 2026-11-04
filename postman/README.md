# Postman: Sentinela API

Import these files into Postman Desktop:

- `Sentinela API.postman_collection.json`
- `Sentinela Local.postman_environment.json`

Select the **Sentinela Local** environment. Update `baseUrl`, credentials, coordinates, codes, and IDs in the environment variables screen. Requests use these variables; login and email confirmation save `accessToken` automatically for protected routes.

Confirmation/recovery codes, `userId`, `googleIdToken`, and credentials are placeholders. Replace them before sending the related requests. **Delete my account** is destructive.

**My profile by ID** and **Count users** inherit the collection Bearer authentication using `accessToken`. For **My profile by ID**, `userId` must identify the authenticated user; querying another user returns 404.

Regenerate the files after editing the request list by running `./generate-assets.ps1` in PowerShell.
