TRANSLATIONS = {
    "_chapter": {
        "title": "Archive タブ",
        "intro": "ファイルがスマートフォンから離れる、唯一の場所です。対象は Archive に設定したアルバムだけで、先に Cloud と照合され、あなたがタップした後にのみ実行されます。",
        "en_hash": "99a3f217222adae4",
    },
    "archive-overview": {
        "title": "Archive のしくみ（手順ごと）",
        "body": """**Archive には Android 11 以降が必要です。**

1. Albums タブで、**アルバムを Archive に設定**して確認します。[[dialog-archive-confirm]]を参照してください。
2. **Check these files を押します。** すべてのファイルが Cloud と照合されます。まだ Cloud にないファイルは、先に送信されます。
3. **結果を確認します。** 各ファイルにチェックマークまたは赤い×が付き、解放される容量が表示されます。
4. **Yes を選びます。** Android が、確認済みのファイルをスマートフォンのゴミ箱に移すかどうか尋ねます。[[dialog-android-trash]]を参照してください。
5. 準備ができたら、**ゴミ箱を空にします。** 容量が戻るのは、そのときだけです。

## 「安全に」の意味
Cloud が、その場で、同じサイズでファイルを保持していると確認する必要があります。Cloud に問い合わせできない場合、何も削除されません。最適化された写真では、元のファイルのサイズが確認されます。

## 対応する Cloud
OneDrive、Google Drive、Dropbox、pCloud、IDrive e2、Backblaze B2。Google Photos は対象外です。

## 知っておくと便利なこと
• Archive には、あなたがその場にいる必要があります。Android がタップを求めるためです。[[dialog-exit-warning]]を参照してください。
• あとから Archive アルバムに追加されたファイルも、対象になります。
• 最後のファイルがなくなると、そのアルバムは一覧から消え、モードも忘れられます。戻ってきた場合は、Off から始まります。""",
        "en_hash": "767ff53f0ad97065",
    },
    "archive-hero": {
        "title": "Files to Archive",
        "body": """**Files to Archive** と、アーカイブされるファイルの数：アルバム一覧ではすべての Archive アルバム分、アルバムを開いている場合はそのアルバム分です。スマートフォンに残すことにしたファイルは、数えません。

その下に、Archive アルバムと、アーカイブされたファイルは **Restore** タブで戻せるという注意書きがあります。""",
        "en_hash": "1b24c615ac7e7cb7",
    },
    "archive-folder-list": {
        "title": "Archive アルバム",
        "body": """Archive アルバムごとに 1 枚のカード：待機中のファイルの数とサイズ、さらに、保持、期間フィルターで除外、確認済み、未確認のものがあれば、その数も表示されます。

• アルバム一覧では、ボタンは **Check all albums** と表示され、すべてのアルバムを確認します。
• アルバムをタップして開くと、**Check these files** は、そのアルバムだけを確認します。

アルバムを選んでも何も削除されず、モードも変わりません。""",
        "en_hash": "e1a713efaeea9168",
    },
    "archive-check-button": {
        "title": "Check these files と、報告される内容",
        "body": """• **Check these files**（または **Check all albums**）：確認を開始します。
• Cloud に問い合わせている間は、**Checking with your Cloud...** と表示されます。
• ファイルを削除している間は、**Batch** の進行状況が表示されます。
• 削除されたファイルの数と解放された容量、または **Nothing was removed.**

OneDrive は、OneDrive 内の各アルバムのフォルダを一覧して（名前とサイズ）確認します。Dropbox、Google Drive、pCloud、Backblaze B2、IDrive e2 は、ファイルごとに問い合わせます。

「解放」とは、ゴミ箱を空にした後のことです。""",
        "en_hash": "651062d720e9e712",
    },
    "archive-age-filter": {
        "title": "Only show files older than…",
        "body": """この確認の対象を、その期間以上前のファイルに限定します：**1 hour** から **1 year**、または **All**。それより新しいファイルはグレー表示になり、次回以降の確認に回されます。

Settings の既定値から始まります。ここで変更しても、その表示中のみ有効です。""",
        "en_hash": "3d5d116005b80474",
    },
    "archive-empty": {
        "title": "アーカイブするものがないとき",
        "body": """• **Archive に設定されたアルバムがない**：Albums タブで設定します。
• **0 件**：Archive アルバム内のすべてが、すでにスマートフォンから移されています。
• **この Android バージョンにはメディアのゴミ箱がない**：Archive には Android 11 以降が必要です。""",
        "en_hash": "ebf6a50a3d8d1d39",
    },
    "archive-file-list": {
        "title": "ファイル一覧とマーク",
        "body": """Archive アルバム内のファイルごとに、1 枚のカードがあります。

## ファイルをスマートフォンに残す
アルバムの残りがアーカイブされても、特定のファイルをスマートフォンに残したいときに使います。ファイルを**左**にスワイプして残します。グレーになり **Not archiving** と表示され、その選択は記憶されます。**右**にスワイプすると、再び対象に含まれます。**too recent for your filter** とグレー表示されたファイルは、期間フィルターで除外されています。

## マーク
• **スピナー**：確認中、送信中、または削除中。
• **緑のチェックマーク**：Cloud で確認済み、または削除済み。
• **赤い×**：未確認。スマートフォンに残ります。

## メッセージ
**Backing up...**、**Moving to Trash/Recycle Bin...**、**Removed from this phone.**、またはファイルが残る理由が赤で表示されます：**Could not check your Cloud**、**Not in your Cloud**、**In your Cloud but the wrong size**。""",
        "en_hash": "4ef90825cb796912",
    },
    "archive-prompt": {
        "title": "質問：All files validated、または一部が validated",
        "body": """確認の後：
• **All files validated**：すべてのファイルが確認済みです。
• **N files validated**：それらだけが確認済みで、残りはスマートフォンに残ります。
• **Nothing can be archived**：確認できたものがありません。**Continue** でメッセージを閉じます。

**Yes** は、確認済みのファイルをゴミ箱に移すよう Android に依頼します。**No** では何も削除されません。大きなアルバムでは、Android の確認が複数回必要になることがあります（各 2,000 ファイル）。""",
        "en_hash": "4155582f22850887",
    },
    "dialog-android-trash": {
        "title": "Android 自身の確認",
        "body": """Android が、ファイルをゴミ箱に移す許可を求めます。文言は機種によって異なります。

• Android は 1 回の依頼で 2,000 ファイルまで許可するため、大きなアルバムはバッチに分けて処理されます。
• 拒否した場合、何も削除されません。""",
        "en_hash": "ea86c62351435e6f",
    },
    "dialog-exit-warning": {
        "title": "Files ready to Archive（終了時のリマインダー）",
        "body": """確認済みでアーカイブ待ちのファイルがある状態で、Back を押して離れようとすると表示されます。

• **Archive now**：Archive タブを開きます。
• **Leave**：アプリを閉じます。何も削除されません。

OneDrive のファイルだけを数えます。事前に確認されるのは、OneDrive のコピーだけだからです。ほかの Cloud は、**Check these files** を押したときに確認されます。ホームを押した場合や、アプリをスワイプして終了した場合、このリマインダーは表示されません。""",
        "en_hash": "973610b6e4444771",
    },
}
