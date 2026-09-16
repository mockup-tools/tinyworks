# tinyworks 共通CI/CD設計書

| 項目 | 内容 |
| --- | --- |
| 文書番号 | 35 |
| 文書版 | 0.1 |
| 対象 | GitHub Actions、Androidビルド、Firebase App Distribution |
| 関連文書 | [開発環境構築手順](30_開発環境構築手順.md) / [公開・運用方針](40_公開・運用方針.md) |

## 1. 基本構成

```text
Pull Request / push
        ↓
GitHub Actions CI
  ├─ testDebugUnitTest
  ├─ lintDebug
  └─ assembleDebug
        ↓ mainで成功
Distribution Workflow
  ├─ 配布用ビルド
  ├─ 固定署名
  └─ Firebase App Distribution
```

## 2. Workflow

```text
.github/workflows/
├─ ci.yml
└─ distribute.yml
```

アプリは一つなので、初期段階では機能ごとのWorkflowを作成しない。

Androidプロジェクトがまだ存在しない段階では、CIは構成確認だけを行い、ビルドジョブをスキップする。`pgm/tinyworks`にGradleプロジェクトを追加した後は、通常のビルド検証を実行する。

## 3. CI

- `main`向けPull Requestで実行する
- 各ブランチへのpushで実行する
- 必要に応じて手動実行できるようにする
- `pgm/tinyworks`をGradleの作業ディレクトリとする
- `testDebugUnitTest`、`lintDebug`、`assembleDebug`を行う
- JDK 17、Android SDK API 37、Build Tools 37.0.0を使用する

## 4. Firebase App Distribution

- `main`への変更がCIに成功した後に配布する
- 配布対象は一つのAndroidアプリとする
- 配布用Debug APKの`applicationId`は暫定で`io.github.mockuptools.tinyworks.debug`とする
- テスト用APKは固定署名鍵で署名する
- Firebase Project ID、Firebase App ID、テスターはGitHub Actions Secretsで管理する
- Firebaseへの自動配布は行うが、Google Playへの自動公開は行わない

## 5. GitHub Actions Secrets

実際の値はリポジトリへ保存しない。

- Firebase認証情報
- Firebase Project ID、App ID、テスター情報
- 配布用署名鍵
- 署名鍵のパスワード

Secret名は`TINYWORKS_`を接頭辞として管理する。

| Secret名 | 用途 |
| --- | --- |
| `TINYWORKS_KEYSTORE_BASE64` | 配布用keystoreのBase64値 |
| `TINYWORKS_STORE_PASSWORD` | keystoreパスワード |
| `TINYWORKS_KEY_ALIAS` | 署名鍵のAlias |
| `TINYWORKS_KEY_PASSWORD` | 署名鍵パスワード |
| `TINYWORKS_FIREBASE_SERVICE_ACCOUNT_JSON` | Firebase App Distribution用認証情報 |
| `TINYWORKS_FIREBASE_PROJECT_ID` | Firebase Project ID |
| `TINYWORKS_FIREBASE_APP_ID` | `io.github.mockuptools.tinyworks.debug`を登録したFirebase Android App ID |
| `TINYWORKS_FIREBASE_TESTER_EMAIL` | 配布先テスター |

## 6. 将来拡張

機能が増えても、まずは一つのAndroidプロジェクト、一つのCI、一本の配布経路を維持する。

機能ごとに個別ビルドや配布が必要になった場合だけ、WorkflowやGradle構成を見直す。

## 7. 未決事項

- 署名方式
- Firebase認証方式（Service Account / OIDC）
- バージョン番号の規則
