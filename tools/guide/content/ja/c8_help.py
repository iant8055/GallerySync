TRANSLATIONS = {
    "_chapter": {
        "title": "質問と問題",
        "intro": "よくある質問と、通常の原因です。",
        "en_hash": "01001cde06eb2f03",
    },
    "faq-sync-now-greyed": {
        "title": "Sync now がグレーアウトしている",
        "body": """送信または最適化するものがありません。すべて送信済みか、Backup、Sync、Archive に設定されたアルバムがありません。ヘッダーのモード合計を確認してください。""",
        "en_hash": "d76ec512e56c7203",
    },
    "faq-albums-off-after-backup": {
        "title": "最初のバックアップの後、アルバムが Off になっている",
        "body": """正常です。最初のバックアップは、モードに関係なくすべてを送信し、セットアップがモードを設定することはありません。Albums タブで、各アルバムのモードを選んでください。""",
        "en_hash": "39db6d0a503fb274",
    },
    "faq-not-checked-yet": {
        "title": "アルバムに Not checked against OneDrive yet と表示される",
        "body": """OneDrive にまだ問い合わせていません。少し待つか、**Rescan** を押してください。**Could not reach OneDrive** は、接続に失敗したという意味です。オンラインでログインしていることを確認して、**Rescan** を押してください。

ほかの Cloud のアルバムには、代わりにその Cloud について **sent to** と表示されます。[[albums-list]]を参照してください。""",
        "en_hash": "52048b6e4b7bcf97",
    },
    "faq-onedrive-full": {
        "title": "Cloud がいっぱいで、バックアップが止まった",
        "body": """**stopped: your Cloud is full.** 何も失われていません。空きを作るかストレージを追加してから、**Sync now** を押してください。""",
        "en_hash": "70148dcb9cb4986c",
    },
    "faq-space-not-freed": {
        "title": "アルバムを Archive したのに、スマートフォンの空きが増えない",
        "body": """アーカイブされたファイルはスマートフォンのゴミ箱にあり、容量を使い続けています。Cloud に安全にあることに納得したら、ゴミ箱を空にしてください。[[where-deleted-files-go]]を参照してください。""",
        "en_hash": "2a38df823e61c58d",
    },
    "faq-album-vanished": {
        "title": "ギャラリーからアルバムが消えた",
        "body": """**Archive** に設定されていた場合、そのファイルはスマートフォンのゴミ箱にあります。ゴミ箱から元に戻すか、**Restore** タブを使ってください。新しいアルバムとして、Off の状態で戻ります。""",
        "en_hash": "94390b0205e85c29",
    },
    "faq-photo-looks-small": {
        "title": "写真が以前より小さい、または画質が低い",
        "body": """最適化されています（小さなクラウドバッジを探してください）。元のファイルは Cloud にあります：**Restore** タブを使ってください。""",
        "en_hash": "4e1a6a3f710360db",
    },
    "faq-restore-empty": {
        "title": "Restore タブに、戻すものがないと表示される",
        "body": """最適化もアーカイブもされたものがなく、（OneDrive では）バックアップフォルダ内のすべての写真がすでにスマートフォンにあります。これは正常です。""",
        "en_hash": "e0720600e98ca0e2",
    },
    "faq-backup-stops-when-closed": {
        "title": "アプリを閉じると、バックアップが止まるようだ",
        "body": """Back やホームなら問題ありません。最近使ったアプリからスワイプして消すと、バックグラウンド処理が止まることがあります。アプリをもう一度開けば、何も二重に送信することなく、続きから再開します。""",
        "en_hash": "266c98c91b9f897f",
    },
    "faq-wrong-account": {
        "title": "間違ったアカウントで Cloud にログインした",
        "body": """その Cloud の横で **Settings → Backup → Sign out** を選び、**Leave them there** を選んでから、正しいアカウントでログインし直します。送信済みのファイルは、最初のアカウントに残ります。[[settings-account]]を参照してください。""",
        "en_hash": "a033d25dffca0535",
    },
    "faq-folder-waiting": {
        "title": "Cloud からログアウトしたら、そのフォルダの送信が止まった",
        "body": """想定どおりです。フォルダは、再びログインするまで、スマートフォン上で安全に待機します。別の送信先にしたい場合は、**Where each folder goes** を変更してください。[[settings-destination]]を参照してください。""",
        "en_hash": "286529a95f8327e7",
    },
    "faq-keys-rejected": {
        "title": "IDrive e2 または Backblaze B2 で、キーが拒否される",
        "body": """ほとんどは入力ミスです。エンドポイント（https:// なし）、リージョン、バケット、両方のキーを確認し、シークレットの **Show** をタップしてください。**O** とゼロ、**I** と **l**、**J** と **j** の取り違えに注意してください。貼り付けると避けられます。[[cloud-key-backblaze]]と[[cloud-key-idrive]]を参照してください。""",
        "en_hash": "e0b15236b548a066",
    },
    "faq-could-not-check": {
        "title": "Archive が、Cloud を確認できなかったと表示する",
        "body": """Cloud が応答しなかったため、何も削除されていません。オンラインでログインしていることを確認して、**Check these files** をもう一度押してください。""",
        "en_hash": "96fcc47fc7eaf6d4",
    },
    "faq-reconnect-cloud": {
        "title": "Restore が、その Cloud の再接続を求める",
        "body": """Cloud が、ファイルの読み取りを拒否しました。Settings でもう一度ログインしてください。Dropbox ではファイルの読み取りを許可し、IDrive e2 と Backblaze B2 ではバケットを読み取れるキーを使ってください。スマートフォンのファイルは変更されていません。""",
        "en_hash": "23bf7817d447a418",
    },
    "albums-permission": {
        "title": "GallerySync が写真を見られない",
        "body": """Albums タブの上部にメッセージが表示されている場合、GallerySync は写真への完全なアクセスを持っていません：
• **GallerySync needs access to your photos**：アクセスが許可されなかったか、取り消されました。
• **Only some photos are shared**：**写真を選択** でアクセスが制限されているため、バックアップが不完全です。

**Grant access** を押して、**すべて許可** を選びます。プロンプトが表示されない場合：スマートフォンの **設定 → アプリ → GallerySync → 権限 → 写真と動画**。""",
        "en_hash": "d1499b80f267c6f8",
    },
    "contact-and-privacy": {
        "title": "連絡先、プライバシー、データの削除",
        "body": """Settings の下部にあります：
• **Contact Info**：アドレスと、コピーボタン。
• **Privacy Policy**：ファイルはご自身の Cloud にのみ送られ、何も収集されません。
• **Delete Account Info**：ログアウト、アクセスの削除、データ消去の方法。Cloud 上のファイルは残ります。

問題を報告するには、そのすぐ上にある **Bug Report** を使ってください。""",
        "en_hash": "3e29c01351fc7319",
    },
}
