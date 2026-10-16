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
        "en_hash": "3c7f69542cac04f4",
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
    "albums-optimised-line": {
        "title": "Optimised and Saved",
        "body": """**Sync** を選択しているとき：Sync アルバムで最適化されたファイルの数と、節約された容量が表示されます。

元のファイルは Cloud に残ります。**Restore** で戻せます。[[how-optimising-works]]を参照してください。""",
        "en_hash": "1fbc295f2322e4d0",
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
• **Sync now**：今すぐバックアップします。Settings で最適化のスイッチがオンで、その **モード** が **手動** の場合は、最適化も行います（[[settings-optimise-mode]]を参照）。送るものも最適化するものもないときは、グレーアウトします。
• **Rescan**：スマートフォンを調べ、OneDrive にもう一度問い合わせます。

## バックアップの実行中
• 進行状況は **Syncing** とパーセンテージで表示されます（ファイル数ではなく、データ量ベース）。
• **Pause**：**Resume** を押すまで、すべてのバックアップを保留します。
• **Stop**：今回の実行を終了します。自動バックアップは続きます。

## 知っておくと便利なこと
• **Sync now** は充電を待ちませんが、Wi-Fi の設定には従います。[[settings-mobile-data]]を参照してください。
• **Backup**、**Sync**、**Archive** に設定されたすべてのアルバムをバックアップしますが、最適化するのは **Sync** アルバムのファイルだけです。""",
        "en_hash": "846b4240970f2d4a",
    },
    "albums-status-line": {
        "title": "カードの下のステータス行",
        "body": """• **アップロード中**：ファイル数、送信中のファイル、その進行状況。
• **準備中**：**Scanning...** または **No permission to read media.**
• **実行後**：該当する内容の概要：**uploaded**、**already in the Cloud**、**no longer on this phone**、**failed**、**waiting on the Cloud**（後で再試行）、**still to go**、または理由付きの **stopped:**。""",
        "en_hash": "78b2b8f3d6801853",
    },
    "albums-list": {
        "title": "Albums タブ",
        "body": """## ヘッダー
• **Backup**、**Sync**、**Archive**、**Off** をタップすると、そのモードのアルバムだけが表示されます。**All Albums** をタップすると、再びすべて表示されます。
• ボタンの下の行に、各モードの合計が表示されます。

## アルバムカード
• **名前、ファイル数、サイズ**：スマートフォン上のアルバムのもの。
• **2 行目**（必要な場合のみ）：最適化済み、フルサイズで保持、保留中、失敗したファイルの数。
• **Cloud 行**：**verified in OneDrive** は、OneDrive が正しいサイズでファイルを確認したことを意味します。タブを開くたびに確認されます。ほかの Cloud では、その Cloud について **sent to** と表示されます。
• **モードボタン**（右側）：タップするとアルバムのモードを変更できます。Archive では、先に確認を求められます。アルバムの Cloud がそのモードに対応していない場合は、カードにその旨が表示されます。
• **カードをタップ**すると、アルバムのファイルが表示されます。[[album-detail]]を参照してください。""",
        "en_hash": "52297f12afc2a7eb",
    },
    "album-detail": {
        "title": "アルバムのファイル一覧",
        "body": """アルバムカードをタップすると開きます。

• **ヘッダー**：戻る矢印、アルバム名、モードとファイル数、**Backs up to**（新しいファイルの送信先の Cloud とフォルダ、およびすでにファイルを保持しているほかの Cloud）、件数（[[album-file-status]]）、**Sort by**（[[album-file-sort]]）、**Keep at full size**（[[album-file-pin]]）。
• **ファイル**：ファイルごとに 1 枚のカードで、サイズ、日付、マークが表示されます。最適化されたファイルには、スマートフォン上の小さいサイズが表示されます。
• **カメラのフォルダ**（Settings で選んだ場合）：追加の最適化コントロール。[[album-camera-optimise]]を参照してください。

サムネイルやプレビューはありません。写真を見るには、ギャラリーアプリを使ってください。""",
        "en_hash": "c5a7c5d01cd7947b",
    },
    "album-file-status": {
        "title": "アルバム内のファイルのマークと件数",
        "body": """• **✓ backed up**：Cloud にあり、スマートフォンではフルサイズ。
• **✓ backed up · optimised**：Cloud にあり、スマートフォンには小さいコピーがあります。
• **⟳ pending**：送信待ち。
• **✗ failed**：何度か試しても送信できませんでした。

## 失敗したものを再試行する
**Retry** は、失敗したファイルをキューに戻して、バックアップを開始します。何も削除しません。""",
        "en_hash": "a1305dbfcdea48b5",
    },
    "album-file-sort": {
        "title": "アルバムのファイルを並べ替える",
        "body": """• **Name**：A から Z の順。
• **Date**：新しい順。復元したファイルの日付は、復元した日です。
• **Status**：failed、pending、optimised、backed up の順。

一覧は、開くたびに名前順になります。並べ替えてもファイルは変わりません。""",
        "en_hash": "030835fb143a638a",
    },
    "album-file-pin": {
        "title": "Keep at full size",
        "body": """**Restore** で戻したファイルの横にあるチェックボックスです。選択している間、GallerySync はそのファイルに手を付けません：
• **Sync** アルバムでは、最適化されません。
• **Archive** アルバムでは、削除されません。
• **Backup** または **Off** では何も変わりませんが、選択は記憶されます。

Restore は自動的に選択します。選択を外すと、ファイルはアルバムのモードに再び従います。""",
        "en_hash": "34b89b8b5829a956",
    },
    "album-camera-optimise": {
        "title": "カメラのフォルダの古い写真と動画を最適化する",
        "body": """Settings の **カメラの Sync**（[[settings-section-camera]]）で選んだ **カメラのフォルダ** にだけ、これらのコントロールがあります。

## ファイルを選択する
• **Select Photos/Videos older than**：**1 day** から **1 year**、または **All**。
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
        "en_hash": "f06a5a7ce89e99c4",
    },
    "album-camera-select": {
        "title": "カメラのフォルダのファイルを選択する",
        "body": """選択したファイルの数と、節約できる推定容量、または **Nothing selected** が表示されます。

• **Photos**、**Videos**、および期間で、一致するものがすべて選択されます。
• 右にスワイプで選択、左にスワイプで選択解除。
• **Sync now** は、選択したファイルだけを最適化します。

グレーのファイルは選択できません。OneDrive でまだ確認されていないか、すでに最適化されています。[[album-camera-optimise]]を参照してください。""",
        "en_hash": "9904773f638bae7b",
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
