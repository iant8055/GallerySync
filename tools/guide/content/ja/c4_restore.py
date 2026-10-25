TRANSLATIONS = {
    "_chapter": {
        "title": "Restore タブ",
        "intro": "Cloud から写真と動画を戻します。Cloud からは読み取るだけで、Cloud 上のものは何も変更されません。",
        "en_hash": "e472722c1c5b626f",
    },
    "restore-overview": {
        "title": "Restore で戻せるもの",
        "body": """• **To restore**：スマートフォン上の、最適化されたファイル。復元すると、フルサイズの元のファイルが元の場所に戻ります。
• **To download**：スマートフォンにもうないファイル。ダウンロードすると、そのアルバムに戻ります。

復元されたファイルには **Keep at full size** が付くため、すぐにまた最適化やアーカイブされることはありません。[[album-file-pin]]を参照してください。

## 対応する Cloud
• **OneDrive**：バックアップフォルダ内のすべての写真と動画（誰が入れたかは問いません）。
• **Dropbox、Google Drive、pCloud、Backblaze B2、IDrive e2**：GallerySync がこのスマートフォンから送ったファイル。
• **Google Photos**：復元元にはできません。

## ファイルの戻り先
元のフォルダに戻ります。**DCIM**、**Pictures**、**Movies** の直下にあったファイルは、そのフォルダに戻ります。フォルダに書き込めない場合は、**DCIM** 内にアルバム名のフォルダが作られ、そこに入ります。

## 試しても安全です
ファイルが置き換えられるのは、ダウンロードが完全に届き、サイズが一致した後だけです。何かが失敗した場合、スマートフォンのファイルはそのままです。""",
        "en_hash": "1bc7e998be6fc106",
    },
    "restore-hero": {
        "title": "Albums to Restore and Files in this album",
        "body": """• **アルバム一覧**：**Albums to Restore** と、戻せるものがあるアルバムの数。
• **アルバムの中**：戻る矢印、アルバム名、**Files in this album**（件数と、選択した数）。
• カードを右にスワイプで選択、左にスワイプで選択解除。タップで開きます。これらはヘッダーが表示されているかに関わらず動作します。
• カード下部の **▲ Hide Details ▲** をタップすると、カードはタイトル、数、メインボタンだけに折りたたまれます。**▼ Show Details ▼** でスワイプの説明、**Refresh** / **Clear**、フォルダの **Sort** / 検索の行が戻ります。結果と選択内容は残ります。タブはこの選択を記憶します。

ダッシュは、一覧がまだ読み込み中であることを示します。**Checking your Cloud for more...** は、OneDrive を読み込み中という意味です。赤いメッセージは、Cloud のフォルダに到達できなかったことを示します。

OneDrive の一覧は記憶され、10 分後または **Refresh** を押したときに、もう一度読み込まれます。""",
        "en_hash": "1099ec37091c62a1",
    },
    "restore-message-line": {
        "title": "数字の下のメッセージ",
        "body": """• アルバム一覧では **Swipe right to select / left to deselect. Tap to open.**
• アルバムの中では **Swipe right to select / left to deselect → Then press Restore.**
• 一覧が空のときは **Nothing in your Cloud is missing from this phone, so there is nothing to bring back.**
• 復元後は、戻ったファイルの数と、変更されなかったファイルの数。""",
        "en_hash": "5b14979898ba9b25",
    },
    "restore-selected-summary": {
        "title": "N selected and how much to recover",
        "body": """すべてのアルバムにわたって選択したファイルと、それらがスマートフォン上で追加で使う容量です。その分の空きがあることを確認してください。""",
        "en_hash": "91898ed468f32d87",
    },
    "restore-buttons": {
        "title": "Refresh, Select all and Clear",
        "body": """• **Refresh**（アルバム一覧）：OneDrive を含め、一覧をもう一度読み込みます。
• **Select all**（アルバムの中）：そのアルバムのすべてのファイルを選択します。
• **Clear**：すべての選択を解除します。

復元の実行中は、グレーアウトします。""",
        "en_hash": "69730fbd93d70feb",
    },
    "restore-folders-list": {
        "title": "アルバムの一覧",
        "body": """アルバムカードごとに、次が表示されます：
• 戻せるファイルの数と、必要な容量。
• **to restore** と **to download** の数。
• **already on this phone** の数（中ではグレー表示。[[restore-greyed-files]]を参照）。
• **selected here** の数。アルバム全体を選択した場合は、チェックマーク。

**タップ**で開きます。**右にスワイプ**するとアルバム全体を選択、**左**にスワイプすると選択解除です。""",
        "en_hash": "54ceae2583fe13a0",
    },
    "restore-files-list": {
        "title": "アルバム内のファイル一覧",
        "body": """右にスワイプ（またはタップ）すると選択、左にスワイプすると選択解除です。各ファイルには、次のいずれかが表示されます：
• **now · full size**：最適化されたコピー。スマートフォン上の小さいファイルサイズと、Cloud 上のフルサイズのファイルサイズが表示されます。
• **Available for download**：スマートフォンにありません。
• **Already on this phone**：グレー表示。何もする必要はありません。

処理中は、パーセンテージ付きの **Working...** が表示されます。その後、理由とともに **Restored to full size**、**Back on this phone**、または **Could not restore, your file is unchanged** が表示されます。""",
        "en_hash": "486e3275bedd2609",
    },
    "restore-greyed-files": {
        "title": "グレー表示のファイルがあるのはなぜですか？",
        "body": """それらはすでにスマートフォンにフルサイズであり（同じアルバム、名前、サイズ）、復元するものがありません。アルバムが Cloud と一致して見えるように、表示されています。

同じ名前でサイズが異なるファイルは、表示されません。編集されたものかもしれず、Restore が編集内容を上書きすることはないからです。""",
        "en_hash": "02372472f6d53985",
    },
    "restore-action-bar": {
        "title": "Restore and Stop restoring",
        "body": """• **Restore**：選択したすべてのファイルを、1 つずつ戻します。何かを選択すると表示されます。
• **Stop restoring**：バッチを止めます。すでに復元されたファイルは、復元されたままです。

進行状況バーに、復元済みの数が表示されます。インターネット接続と、十分な空き容量が必要です。""",
        "en_hash": "52b0a1302c2c28cb",
    },
    "restore-edited": {
        "title": "編集した写真や動画",
        "body": """最適化されたコピーを編集して上書き保存した場合、復元するとその編集内容が置き換わってしまうため、アプリが **This photo has been edited since it was backed up. Overwrite it with the original?** と尋ねます。

• **Keep my edits**：ファイルをそのままにします。
• **Overwrite**：元のファイルを戻します。編集内容は置き換えられます。

## 知っておくと便利なこと
ほとんどの編集アプリは新しいファイルとして保存するため、影響を受けません。編集されたファイルは、名前に **edited** と日付が付いた新しいファイルとしてバックアップされます。Cloud 上の元のファイルが置き換えられることはありません。""",
        "en_hash": "6292714bbfa760d3",
    },
}
