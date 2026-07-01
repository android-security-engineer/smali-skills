# 发布指南（Publishing）

本仓库有四条分发渠道，均由 `v*` tag 触发对应的 GitHub Actions workflow。

| 渠道 | 产物 | Workflow | 所需 Secrets |
|------|------|----------|--------------|
| GitHub Release | fat jar + skills + install.sh | `release.yml` | 无（用内置 `GITHUB_TOKEN`） |
| Maven Central | `dexlib2` / `util` / `smali` / `baksmali` 库 jar | `maven-publish.yml` | `SONATYPE_*`、`GPG_SIGNING_*` |
| Docker (GHCR) | `ghcr.io/.../smali-skills` 镜像 | `docker-publish.yml` | 无（内置 `GITHUB_TOKEN` + `packages:write`） |
| Homebrew | `Formula/smali-skills.rb` → tap 仓库 | `release.yml`（Update Homebrew tap 步骤） | `HOMEBREW_TAP_TOKEN` |

## Maven Central

命名空间 `io.github.android-security-engineer`（GitHub 组织名，可在 Central Portal 自助验证）。

### 一次性准备

1. 在 <https://central.sonatype.com> 注册，用 `io.github.android-security-engineer` 命名空间
   （通过在 GitHub 组织下建一个校验用仓库完成 DNS/GitHub 验证）。
2. 生成 User Token（Account → Generate User Token），得到 `username` / `password`。
3. 生成 GPG 密钥并上传公钥到公共 keyserver：
   ```bash
   gpg --gen-key
   gpg --armor --export-secret-keys <KEY_ID>   # 用作 GPG_SIGNING_KEY
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```
4. 在仓库 Settings → Secrets 配置：
   `SONATYPE_USERNAME`、`SONATYPE_PASSWORD`、`GPG_SIGNING_KEY`、`GPG_SIGNING_PASSWORD`。

### 发布

打 tag 即触发；或手动：

```bash
./gradlew publish \
  -PsonatypeUsername=<token-name> \
  -PsonatypePassword=<token-secret> \
  -PsigningKey="$(cat key.asc)" \
  -PsigningPassword=<passphrase>
```

产物进入 Central Portal 的 staging，登录后点 **Publish** 正式发布（也可开启自动 release）。

本地验证 POM/签名而不上传：

```bash
./gradlew publishToMavenLocal        # 写入 ~/.m2，signing 非必需
./gradlew generatePomFileForMavenJavaPublication
```

> 覆盖命名空间：`-PmavenGroup=com.example`（默认 `io.github.android-security-engineer`）。

## Docker (GHCR)

`docker-publish.yml` 在 tag 时构建多阶段镜像并推送到
`ghcr.io/android-security-engineer/smali-skills:<version>` 与 `:latest`。本地：

```bash
docker build -t smali-skills .
docker run --rm -v "$PWD:/work" smali-skills list strings app.apk
```

## Homebrew

`packaging/homebrew/smali-skills.rb` 是模板；`release.yml` 的 *Update Homebrew tap*
步骤在发布后计算 jar 的 sha256，回填 `version`/`url`/`sha256`，推送到
`android-security-engineer/homebrew-tap` 的 `Formula/smali-skills.rb`（需 `HOMEBREW_TAP_TOKEN`）。

首次需手动建 `homebrew-tap` 仓库。之后用户：

```bash
brew tap android-security-engineer/tap
brew install smali-skills
```
