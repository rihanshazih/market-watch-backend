# Eve Market Watch

## Deployment Guide

In order to deploy this application you need an AWS account with the permissions to provision Lambdas and DynamoDB.

First create the following DynamoDB tables:
- `eve_marketwatch_user` with number key `character_id`
- `eve_marketwatch_structure` with number key `structure_id`
- `eve_marketwatch_item_watch` with string key `id`
- `eve_marketwatch_item_snapshot` with string key `id`
- `eve_marketwatch_mail` with string key `id`

Make sure you haven [maven](https://maven.apache.org/install.html), [jdk8](https://openjdk.java.net/install/), [npm](https://www.npmjs.com/get-npm), [aws cli](https://docs.aws.amazon.com/en_en/cli/latest/userguide/installing.html) and [serverless framework](https://serverless.com/framework/docs/getting-started/) installed.

Create two eve apps. One for the mail sender (scope: `esi-mail.send_mail.v1`) and one for the users (scopes: `esi-search.search_structures.v1 esi-universe.read_structures.v1 esi-markets.structure_markets.v1`). Manually create a refresh token for the mail sender.

Create a config.<stage>.json with the following content:
```json
{
  "adminCharacterId": 123,
  "jwtSecret": "123",
  "mailCharacterId": 123,
  "mailClientId": "123",
  "mailClientSecret": "123",
  "mailRefreshToken": "123",
  "appClientId": "123",
  "appClientSecret": "123"
}

```

In the root of this project run `mvn clean package` to build the artifact and `sls deploy` to deploy it.

After the deployment is complete (which make take a couple minutes) you will see the URLs of the endpoint in the console.
Use the endpoint URL for the [frontend project]().

## Contributing

Contributions are welcome! Discuss, fork the repo and then create a pull request.
