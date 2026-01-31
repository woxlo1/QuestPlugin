# QuestPlugin

Minecraftサーバー用のクエスト管理プラグインです
多彩なクエストタイプに対応し、パーティー機能や進行共有などの拡張も可能です

**バージョン: 2.2.0 (Build 22)**  
最終更新: 2025/1/30

---

## ✨ 新機能 (v2.2.0)

### 🗄️ MySQL/MariaDBデータベース対応
- **データベースストレージ**: 大規模サーバーや複数サーバー連携に最適
- **YAML保存**: 小規模サーバー向けのシンプルな設定（従来通り）
- **動的切り替え**: コマンドで現在のストレージモードを確認可能
- **自動フォールバック**: DB接続失敗時も安全にYAMLで動作
- **非同期処理**: メインスレッドをブロックせず高パフォーマンス

### 保存内容
- ✅ クエスト進行状況（リアルタイム保存）
- ✅ クールダウン・使用回数管理
- ✅ クエスト履歴（成功/失敗の記録）

---

## 機能

- 討伐、収集、探索、採掘、設置など多種多様なクエストタイプ対応
- 時間制限付きクエストの実装
- クエスト進行状況のBossBar表示
- クエストクリアでコマンド実行による報酬付与
- コマンドベースでのクエスト作成・編集・開始・一覧表示
- パーティー機能によるクエスト進行の共有や共同プレイ対応
- **MySQL/YAMLハイブリッドストレージ対応**

---

## 📦 インストール

### 基本セットアップ

1. プラグインのjarファイルを `plugins` フォルダに入れる
2. `plugins/QuestPlugin`のフォルダを手動作成し`config.yml`のファイルを作成する
3. サーバーを起動する
4. `quests.yml` でクエスト設定を管理

### データベースセットアップ（オプション）

#### YAMLモード（デフォルト）
```yaml
# config.yml
database:
  enabled: false
```
小規模サーバー向け。設定変更不要でそのまま使用可能。

#### MySQLモード
```yaml
# config.yml
database:
  enabled: true
  host: "localhost"
  port: 3306
  database: "minecraft"
  username: "minecraft"
  password: "secure_password"
  useSSL: false
```

**データベース作成:**
```sql
CREATE DATABASE minecraft CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'minecraft'@'localhost' IDENTIFIED BY 'secure_password';
GRANT ALL PRIVILEGES ON minecraft.* TO 'minecraft'@'localhost';
FLUSH PRIVILEGES;
```

**依存関係追加（build.gradle.kts）:**
```kotlin
dependencies {
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("mysql:mysql-connector-java:8.0.33")
}
```

---

## 📜 コマンド一覧

### 🔧 クエスト設定コマンド

| コマンド                                                  | 説明               | 権限                             |
|-------------------------------------------------------|------------------|--------------------------------|
| `/quest config create <ID>`                           | 新しいクエストを作成する     | `quest.config.create`          |
| `/quest config set <ID> name <名前>`                    | クエストの名前を設定する     | `quest.config.name`            |
| `/quest config set <ID> type <タイプ名>`                  | クエストのタイプを設定する    | `quest.config.set`             |
| `/quest config set <ID> target <ターゲット名>`              | クエストのターゲットを設定する  | `quest.config.target`          |
| `/quest config set <ID> amount <数値>`                  | クリアに必要な数を設定する    | `quest.config.amount`          |
| `/quest config set <ID> timelimit <秒数>`               | 制限時間を秒数で設定する     | `quest.config.timelimit`       |
| `/quest config set <ID> cooldown <秒数>`                | クールダウン秒数を設定する    | `quest.config.cooldown`        |
| `/quest config set <ID> maxuse <回数>`                  | 最大使用回数を設定する      | `quest.config.maxuse`          |
| `/quest config set <ID> maxlives <ライフ数>`              | 最大ライフを設定する       | `quest.config.maxlives`        |
| `/quest config set <ID> partyenabled <true/false>`    | パーティー機能を有効/無効にする | `quest.config.partyenabled`    |
| `/quest config set <ID> shareprogress <true/false>`   | 進行状況の共有を有効/無効にする | `quest.config.shareprogress`   |
| `/quest config set <ID> sharecompletion <true/false>` | 達成状態の共有を有効/無効にする | `quest.config.sharecompletion` |
| `/quest config set <ID> partymaxmembers <数値>`         | パーティー最大人数を設定する   | `quest.config.partymaxmembers` |
| `/quest config set <ID> teleportworld <ワールド名>`        | テレポート先のワールドを設定する | `quest.config.teleportworld`   |
| `/quest config set <ID> teleportx <X座標>`              | テレポート先のX座標を設定する  | `quest.config.teleportx`       |
| `/quest config set <ID> teleporty <Y座標>`              | テレポート先のY座標を設定する  | `quest.config.teleporty`       |
| `/quest config set <ID> teleportz <Z座標>`              | テレポート先のZ座標を設定する  | `quest.config.teleportz`       |
| `/quest config save`                                  | クエスト設定を保存する      | `quest.config.save`            |
| `/quest wand`                                         | 範囲指定用ワンドを取得する    | `quest.wand`                   |

---

### 🚀 クエスト実行コマンド

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/quest start <ID>` | クエストを開始する | `quest.start` |
| `/quest leave` | クエストを中断する | `quest.leave` |
| `/quest info <ID>` | クエストの詳細を見る | `quest.info` |
| `/quest list` | 使用可能なクエストを一覧表示する | `quest.use` |
| `/quest reload` | プラグイン設定を再読み込みする | `quest.reload` |

---

### 🗄️ ストレージ管理コマンド **[NEW!]**

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/quest storage` | 現在のストレージモードを確認する | `quest.storage` |
| `/quest storage status` | ストレージの詳細情報を表示する | `quest.storage` |
| `/quest storage mysql` | MySQLモードへの切り替え方法を表示 | `quest.storage` |
| `/quest storage yaml` | YAMLモードへの切り替え方法を表示 | `quest.storage` |

---

### 💰 経済関連コマンド

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/quest deposit <player> <金額>` | プレイヤーにお金を付与する | `quest.money` |
| `/quest withdraw <player> <金額>` | プレイヤーからお金を引き出す | `quest.money` |

---

### 👥 パーティーコマンド

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/quest party` | 自分のパーティー情報を表示する | `quest.party` |
| `/quest party create` | パーティーを作成する | `quest.party` |
| `/quest party invite <player>` | プレイヤーをパーティーに招待する | `quest.party` |
| `/quest party join <player>` | プレイヤーのパーティーに参加する | `quest.party` |
| `/quest party leave` | パーティーを離脱する | `quest.party` |
| `/quest party disband` | パーティーを解散する（リーダーのみ） | `quest.party` |
| `/quest party kick <player>` | パーティーからプレイヤーを追放する | `quest.party` |

---

### 📝 ログ閲覧コマンド

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/quest logop <player> <ページ数>` | 指定プレイヤーのクエスト履歴を確認する | `quest.logop` |

---

## クエスト設定項目

| key               | 説明                       | 例                                 |
|-------------------|--------------------------|-----------------------------------|
| `name`            | クエストの名前                  | `ドラゴン討伐`                          |
| `type`            | クエストのタイプ                 | `KILL`, `COLLECT` など（QuestType参照） |
| `target`          | 対象のMob名・アイテム名など          | `ZOMBIE`、`DIAMOND`                |
| `amount`          | 必要な数                     | `10`                              |
| `timelimit`       | 制限時間（秒、0または未設定で無制限）      | `300`（5分）                         |
| `cooldownSeconds` | クールダウン時間（秒）              | `600`（10分）                        |
| `maxUseCount`     | 最大挑戦回数（0または未設定で無制限）      | `1`（1回）                           |
| `maxLives`        | クエスト中に最大まで死ねる回数          | `1`（1回）                           |
| `rewards`         | クリア時に実行するコマンド(手動で追加推奨)   | `give %player% diamond 5`         |
| `startCommands`   | スタート時に実行するコマンド           | `give %player% stone_sword`       |
| `partyEnabled`    | パーティーでの共有有効化（true/false） | `true`                            |
| `partyMaxMembers` | クエストに挑める最大パーティー人数        | `3`                               |
| `shareProgress`   | パーティー内で進行状況を共有するか        | `true`                            |
| `shareCompletion` | パーティー内でクリア状態を共有するか       | `true`                            |
| `teleportWorld`   | プレイヤーを指定したワールドに飛ばす       | `world`                           |
| `teleportX`       | 指定したX座標に飛ばす              | `0`                               |
| `teleportY`       | 指定したY座標に飛ばす              | `64`                              |
| `teleportZ`       | 指定したZ座標に飛ばす              | `0`                               |

```yaml
quests:
  test:
    name: "ドラゴン討伐"
    type: "KILL"
    target: "ENDER_DRAGON"
    amount: 1
    timelimit: 1800
    cooldownSeconds: 3600
    rewards:
      - "give %player% diamond 10"
      - "say %player% がドラゴン討伐クエストをクリアしました！"
      - "eco give %player% 1000"
```

---

## 対応クエストタイプ一覧

- KILL (Mob討伐)
- COLLECT (アイテム収集)
- TRAVEL (場所訪問)
- MINE (ブロック採掘)
- PLACE (ブロック設置)
- BREAK (ブロック破壊)
- CRAFT (クラフト)
- SMELT (精錬)
- FISH (釣り)
- SLEEP (寝る)
- CHAT (チャット送信)
- COMMAND (コマンド実行)
- INTERACT (対話)
- DAMAGE_TAKEN (ダメージを受ける)
- DAMAGE_GIVEN (ダメージを与える)
- SHOOT (弓で攻撃)
- MYTHIC_KILL (MythicMobsを討伐する)
- LEVEL (レベル到達)
- EXP_GAINED (経験値獲得)
- TIME_PLAYED (プレイ時間)
- TAME (動物を手懐ける)
- BREED (繁殖)
- TRADE (村人と取引)
- RIDE (乗り物に乗る)

---

## 🎯 今後の開発目標（ロードマップ）

クエスト体験のさらなる進化を目指し、以下の機能追加・改善を計画しています

### 💬 1. ストーリー・連続クエスト対応
- 連続型クエストの実装（順番に進行し、前提達成で次が開放）
- 将来的には分岐型ストーリーや選択肢による進行変化にも対応予定

### 📆 2. デイリー／ウィークリークエスト
- 毎日・毎週更新されるクエスト機能を追加
- 一定回数クリアで特別報酬がもらえるなどの仕組みを導入

---

## 📈 パフォーマンス最適化

### 実装済みの最適化
- ✅ HikariCP（高速コネクションプール）
- ✅ 非同期データベース操作
- ✅ プリペアドステートメント
- ✅ インデックス最適化
- ✅ 定期的な自動保存（5分ごと）

### 推奨設定
- 同時接続プレイヤー数 < 50人: **YAML**
- 同時接続プレイヤー数 50-200人: **MySQL（ローカル）**
- 同時接続プレイヤー数 200人以上: **MySQL（専用サーバー）**

---

## 🧩 その他予定・改善点

- 今後パーティー機能やクエストタイプのさらなる拡充を予定
- ~~複数パーティーが同じ場所にテレポートされないように調整予定~~
- ~~テレポート座標の自動分散、エリア予約などを検討中~~

---

## ライセンス

MIT License

---

## 補足

- スタートコマンドや報酬コマンドの設定ができるコマンドは用意していないので手動操作でお願いします
- 導入必須プラグイン:
    - MythicMobs
    - Vault
    - WorldEdit
    - WorldGuard

---

## 使用させてもらった外部プラグイン/ライブラリ

- [McUtils](https://github.com/shojabon/McUtils)
- [HikariCP](https://github.com/brettwooldridge/HikariCP)
- [MySQL Connector/J](https://dev.mysql.com/downloads/connector/j/)