# ShopPlugin

Paper 1.21+ shop plugin: `/shop`, `/quickbuy` (`/qshop`), `/shards`.
Requires Vault and an economy plugin.

## Build
Push to GitHub, then open the **Actions** tab, pick the latest run and download the
`ShopPlugin` artifact. Or locally: `mvn package`.

## Release
`git tag v1.0.0 && git push --tags` attaches the jar to a GitHub Release.
