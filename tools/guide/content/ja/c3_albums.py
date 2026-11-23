TRANSLATIONS = {
    "_chapter": {
        "title": "Albums タブ",
        "intro": "各アルバムのモードを選び、バックアップを実行する場所です。",
        "en_hash": "7c39f69f07321c58",
    },
    "albums-overview": {
        "title": "Albums タブの構成",
        "body": """• **緑のヘッダー**：モードボタン、合計、バックアップの操作。
• **ステータス行**：何かが実行中のとき、または完了した直後に表示されます。
• **アルバムの一覧**：アルバムごとに 1 枚のカードで、A から Z の順。ワイド画面では 2 列になります。

タブを開くたびに、スマートフォンを調べ、OneDrive にもう一度問い合わせます（**Rescan** は **Checking...** と表示されます）。ほかの Cloud への問い合わせは、ファイルを最適化または削除する直前に行われます。

表示、絞り込み、スクロールでファイルが変わることはありません。変わるのは、モードを選んだときだけです。""",
        "en_hash": "e3efef2f9212aaf3",
    },
    "albums-new": {
        "title": "新しいアルバム",
        "body": """新しいアルバムが現れると、ポップアップに **1 new Album is waiting for you to choose a mode** と表示されます。
• **Show new Albums**：新しいアルバムだけを表示します。**Show all Albums** で、残りも表示に戻ります。
• **Dismiss**：メッセージを閉じます。

新しいアルバムには **New** タグが付き、必ず **Off** から始まります。新しいアルバムが Archive から始まることはありません。""",
        "en_hash": "8f8d4b55c34a1c4d",
    },
    "albums-filter": {
        "title": "All Albums と 4 つのモードボタン",
        "body": """• **Backup**、**Sync**、**Archive**、**Off** をタップすると、そのモードのアルバムだけが表示されます。
• **All Albums**（または同じボタンをもう一度）をタップすると、すべて表示されます。

絞り込みで変わるのは、表示だけです。モードを変えるには、アルバムのカードにあるボタンを使います。[[modes-in-depth]]を参照してください。""",
        "en_hash": "d4865c5cf08d3857",
    },
    "albums-totals": {
        "title": "Album Mode Count",
        "body": """**Album Mode Count**：各モードの合計。**All Albums** を選択しているときに表示されます。**Off** のアルバムは、まだ決めていないものです。""",
        "en_hash": "61afcd38a3bee22c",
    },
    "albums-all-backed-up": {
        "title": "Everything here is backed up",
        "body": """表示中のすべてのアルバムにファイルがあり、送信待ちのものがない場合に表示されます。これはアプリ自身の記録に基づくものです。Cloud 自身の回答は、各アルバムカードの最後の行にあります。""",
        "en_hash": "79b43044d6005ada",
    },
    "albums-optimized-line": {
        "title": "Optimized and Saved",
        "body": """**Sync** を選択しているとき：Sync アルバムで最適化されたファイルの数と、節約された容量が表示されます。

元のファイルは Cloud に残ります。**Restore** で戻せます。[[how-optimizing-works]]を参照してください。""",
        "en_hash": "701f3a7e8e63d389",
    },
    "albums-archive-lines": {
        "title": "Files archived and Scheduled to leave this phone",
        "body": """**Archive** を選択しているとき：
• **N files archived · X in your Cloud**：スマートフォンから移されたファイルと、そのコピーが Cloud で占める容量。
• **N Scheduled to leave this phone**：Archive アルバムの中で、まだスマートフォンにあるファイル（保持を選んだファイルは含みません）。ない場合は **Nothing left to archive** と表示されます。

ここでは何も削除されません。削除は Archive タブで行われます。[[archive-overview]]を参照してください。""",
        "en_hash": "00b01adb4a7cf037",
    },
    "albums-run-controls": {
        "title": "Sync now, Rescan, Pause, Resume and Stop",
        "body": """## 何も実行していないとき
• **Sync now**：今すぐバックアップします。Settings で最適化のスイッチがオンで、その **モード** が **手動** の場合は、最適化も行います（[[settings-optimize-mode]]を参照）。送るものも最適化するものもないときは、グレーアウトします。
• **Rescan**：スマートフォンを調べ、OneDrive にもう一度問い合わせます。

## バックアップの実行中
• 進行状況は **Syncing** とパーセンテージで表示されます（ファイル数ではなく、データ量ベース）。
• **Pause**：**Resume** を押すまで、すべてのバックアップを保留します。
• **Stop**：今回の実行を終了します。自動バックアップは続きます。

## 知っておくと便利なこと
• **Sync now** は充電を待ちませんが、Wi-Fi の設定には従います。[[settings-mobile-data]]を参照してください。
• **Backup**、**Sync**、**Archive** に設定されたすべてのアルバムをバックアップしますが、最適化するのは **Sync** アルバムのファイルだけです。""",
        "en_hash": "4c3628a3516a9b52",
    },
    "albums-status-line": {
        "title": "アップロードの進行状況とステータスメッセージ",
        "body": """• **アップロード中**（カード内）：ファイル数、アルバム、送信中のファイル、そしてそのファイルの合計サイズに対する送信済みバイト数。ファイルサイズがまだわからないあいだは、代わりにパーセンテージが表示されます。その下に進行状況バーが並びます。**▲ Hide Details ▲** で両方を隠せます。
• **準備中**（カードの下）：**Scanning...** または **No permission to read media.**
• **実行後**（カードの下）：該当する内容の概要：**uploaded**、**already in the Cloud**、**no longer on this phone**、**failed**、**waiting on the Cloud**（後で再試行）、**still to go**、または理由付きの **stopped:**。""",
        "en_hash": "5e5b683e646d7541",
    },
    "albums-list": {
        "title": "Albums タブ",
        "body": """## ヘッダー
• **Backup**、**Sync**、**Archive**、**Off** をタップすると、そのモードのアルバムだけが表示されます。**All Albums** をタップすると、再びすべて表示されます。
• ボタンの下の行に、各モードの合計が表示されます。
• カード下部の **▲ Hide Details ▲** でモード合計の行が隠れ、実行中はアップロード行とその進行状況バーも一緒に隠れます。**▼ Show Details ▼** で戻ります。**All Albums**、4 つのモードボタン、**Sync now** / **Rescan** はそのまま残り、実行中は **Syncing…**、**Pause**、**Stop** も残ります。タブはこの選択を記憶します。

## アルバムカード
• **名前、ファイル数、サイズ**：スマートフォン上のアルバムのもの。
• **2 行目**（必要な場合のみ）：最適化済み、フルサイズで保持、保留中、失敗したファイルの数。
• **Cloud 行**：**N verified in <Cloud>** または **N of M verified in <Cloud>** は、その Cloud が正しいサイズでファイルを確認したことを意味します。OneDrive、Dropbox、Google Drive、pCloud、Backblaze B2、IDrive e2 はそれぞれ、タブを開くたびと **Rescan** のときに確認されます。**Could not reach <Cloud>** は、前回の確認が失敗したことを意味します。最初の確認の前、および問い合わせできない Google Photos では、常に **N sent to <Cloud>** と表示されます。表示上のすべての Cloud がすべてを確認済みのとき、この行は緑色になります。
• **モードボタン**（右側）：タップするとアルバムのモードを変更できます。Archive では、先に確認を求められます。アルバムの Cloud がそのモードに対応していない場合は、カードにその旨が表示されます。
• **カードをタップ**すると、アルバムのファイルが表示されます。[[album-detail]]を参照してください。""",
        "en_hash": "d76ad64ef37ca818",
    },
    "album-detail": {
        "title": "アルバムのファイル一覧",
        "body": """アルバムカードをタップすると開きます。

• **ヘッダー**：戻る矢印、アルバム名、モードとファイル数、**Backs up to**（新しいファイルの送信先の Cloud とフォルダ、およびすでにファイルを保持しているほかの Cloud）、件数（[[album-file-status]]）、**Sort by**（[[album-file-sort]]）、**Keep at full size**（[[album-file-pin]]）。
• ヘッダー下部の **▲ Hide Details ▲** をタップすると、ヘッダーはアルバム名、モード、ファイル数だけに折りたたまれます。**▼ Show Details ▼** で残りが戻ります。ページはこの選択を記憶します。
• **ファイル**：ファイルごとに 1 枚のカードで、サイズ、日付、マークが表示されます。最適化されたファイルには、スマートフォン上の小さいサイズが表示されます。
• **カメラのフォルダ**（Settings で選んだ場合）：追加の最適化コントロール。[[album-camera-optimize]]を参照してください。

サムネイルやプレビューはありません。ファイルを**長押し**すると、スマートフォンの写真・動画アプリで開きます。スマートフォンにない場合は、その Cloud で開きます。[[settings-open-not-on-phone]]を参照してください。""",
        "en_hash": "daf3817bf4e259d3",
    },
    "album-file-status": {
        "title": "アルバム内のファイルのマークと件数",
        "body": """• **✓ backed up**：Cloud にあり、スマートフォンではフルサイズ。
• **✓ backed up · optimized**：Cloud にあり、スマートフォンには小さいコピーがあります。
• **⟳ pending**：送信待ち。
• **✗ failed**：何度か試しても送信できませんでした。

## 失敗したものを再試行する
**Retry** は、失敗したファイルをキューに戻して、バックアップを開始します。何も削除しません。""",
        "en_hash": "0b4ffba87f22d1c6",
    },
    "album-resend": {
        "title": 'バックアップされていないファイル',
        "body": """このアルバムが送信したものの、送信先の Cloud にもうないファイルです。Albums タブの **Rescan** で見つかり、アルバムのカードに **N files not backed up** として赤字で件数が出ます。

• それぞれ淡色で表示され、**not in <Cloud>** と表示されます。
• 自動で再送信されることはありません。Cloud で意図して削除した可能性があるためです。
• 右にスワイプで選択、左にスワイプで選択解除。**Select all** と **Clear** はすべてに適用されます。
• **Resend** は選択したファイルを、そのフォルダの現在の送信先の Cloud に再送信します。何も削除しません。
• 最適化したファイルは表示されません。フルサイズの元のファイルは Cloud にしかなかったためです。
• **Off** のアルバムでは、先にモードを選んでください。
• OneDrive 以外の Cloud では、1 回の Rescan で、アルバムのフォルダにないファイルをアルバムごとに最大 25 件まで確認します。残りはもう一度 Rescan してください。""",
        "en_hash": "966ae07aace959b6",
    },

    "album-file-sort": {
        "title": "アルバムのファイルを並べ替える",
        "body": """• **Name**：A から Z の順。
• **Date**：新しい順。
• **Status**：failed、pending、optimized、backed up の順。

一覧は、開くたびに名前順になります。並べ替えてもファイルは変わりません。""",
        "en_hash": "768559e84cdab251",
    },
    "album-file-pin": {
        "title": "Keep at full size",
        "body": """**Restore** で戻したファイルの横にあるチェックボックスです。選択している間、GallerySync はそのファイルに手を付けません：
• **Sync** アルバムでは、最適化されません。
• **Archive** アルバムでは、削除されません。
• **Backup** または **Off** では何も変わりませんが、選択は記憶されます。

Restore は自動的に選択します。選択を外すと、ファイルはアルバムのモードに再び従います。""",
        "en_hash": "3691f19075c93c85",
    },
    "album-camera-optimize": {
        "title": "カメラのフォルダの古い写真と動画を最適化する",
        "body": """Settings の **カメラの Sync**（[[settings-section-camera]]）で選んだ **カメラのフォルダ** にだけ、これらのコントロールがあります。

## ファイルを選択する
• **Select Photos/Videos older than**：**½** から **9** までの数字と、**hour**、**day**、**week**、**month**、**year**、または **All** のいずれかの単位。
• **Photos** と **Videos**：選択する種類。
• ファイルを右にスワイプすると選択、左にスワイプすると選択解除になります。カードに、選択した数と推定節約量が表示されます。

## ボタン
• **Sync now**：フォルダ内で待機中のものをバックアップしてから、選択したファイルだけを最適化します。
• **Rescan**：Cloud をもう一度確認します。

## 知っておくと便利なこと
• OneDrive がフルサイズで確認したファイルだけが、選択できます。
• **Sync now** を押すまで、何も最適化されません。新しい写真はそのままです。
• カメラのフォルダには **Sync** モードがなく、Settings の最適化スイッチも影響しません。
• 選択を変更したまま離れると、それを既定にするかどうか尋ねられます。""",
        "en_hash": "19ad4bbf62950ae9",
    },
    "album-camera-select": {
        "title": "カメラのフォルダのファイルを選択する",
        "body": """選択したファイルの数と、節約できる推定容量、または **Nothing selected** が表示されます。

• **Photos**、**Videos**、および期間で、一致するものがすべて選択されます。
• 右にスワイプで選択、左にスワイプで選択解除。
• **Sync now** は、選択したファイルだけを最適化します。

グレーのファイルは選択できません。OneDrive でまだ確認されていないか、すでに最適化されています。[[album-camera-optimize]]を参照してください。""",
        "en_hash": "ec8dfe5b2063e196",
    },
    "album-merge-warning": {
        "title": "重複したアルバム名を検出しました",
        "body": """1 つのフォルダが、大文字と小文字だけが違う 2 つの表記で記録されることがあります。GallerySync はこれを検出して 1 つのアルバムに統合し、改めて選べるようにモードを **Off** にします。**Dismiss** でカードを閉じます。

アプリがモードを設定するのはこのときだけで、設定できるのは **Off** だけです。""",
        "en_hash": "559da2287e55a253",
    },
    "dialog-archive-confirm": {
        "title": "Archive this album?",
        "body": """**Archive** を選ぶと表示されます：
• Archive は、すべてのファイルが Cloud にあることを確認します。
• アーカイブされたファイルは、スマートフォンのゴミ箱に移動します。
• 容量を空けるには、ゴミ箱を空にします。

**Archive** で確定し、**Cancel** では何も変わりません。

## 知っておくと便利なこと
確認は 1 回だけです。あとからアルバムに追加されたファイルも、アルバムが空になるまで対象です。Android は、最大 2,000 ファイルのバッチごとに 1 回確認します。[[dialog-android-trash]]を参照してください。""",
        "en_hash": "ac36f7c88ce4bc76",
    },
}
