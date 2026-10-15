TRANSLATIONS = {
    "_chapter": {
        "title": "Settings टैब",
        "intro": "हरे बैंड में एक स्क्रॉल होने वाला पेज: General, Backup, Albums, Sync, Camera Sync, Restore, Archive और Help & Feedback। हर बैंड में एक (?) है।",
        "en_hash": "fdc210cbde6172d8",
    },
    "settings-overview": {
        "title": "Settings टैब का लेआउट",
        "body": """\
• बदलाव तुरंत लागू हो जाते हैं। सेव करने का कोई बटन नहीं है।
• यहाँ कुछ भी आपके फ़ोन से कोई फ़ाइल नहीं हटाता। यह सिर्फ़ Archive करता है।
• Settings और पहली बार का सेटअप अलग-अलग हैं। देखें [[setup-and-settings]]।""",
        "en_hash": "523b8f790e8db56b",
    },
    "settings-section-general": {
        "title": "General",
        "body": """\
**How To Guide**, **Appearance** और **Use mobile data**। **Language** पेज की आखिरी लाइन है।""",
        "en_hash": "b8038bbcadfcd8b3",
    },
    "settings-how-to-guide-card": {
        "title": "How To Guide कार्ड",
        "body": """\
यह यही गाइड खोलता है। किसी भी **(?)** में **Read more in the How To Guide** उसे उसी विषय पर खोलता है। इंटरनेट कनेक्शन चाहिए; अगर लोड न हो, तो **Open in browser** इस्तेमाल करें।""",
        "en_hash": "a7dec55c7b2218a4",
    },
    "settings-language": {
        "title": "Language",
        "body": """\
वह भाषा जो आपने स्वागत स्क्रीन पर चुनी थी। बदलने के लिए उस पर टैप करें। How To Guide इसी चुनाव का पालन करती है; ऐप की स्क्रीन फ़िलहाल अंग्रेज़ी में हैं।""",
        "en_hash": "e1597813fc836e98",
    },
    "settings-appearance": {
        "title": "Appearance",
        "body": """\
• **System** (डिफ़ॉल्ट): आपके फ़ोन की लाइट या डार्क सेटिंग का पालन करता है।
• **Light** या **Dark**: हमेशा वही रूप।

गाइड और पॉलिसी पेज आपके फ़ोन की सेटिंग का पालन करते हैं।""",
        "en_hash": "b2ab8ff47282bc06",
    },
    "settings-mobile-data": {
        "title": "Use mobile data",
        "body": """\
• **Off** (डिफ़ॉल्ट): **Wi-Fi only.**
• **On:** **Syncs on Wi-Fi and mobile data.**

अपने-आप होने वाले बैकअप और **Sync now** पर लागू होता है। जब तक आपका डेटा प्लान अनलिमिटेड न हो, इसे बंद रखें।""",
        "en_hash": "e8860c60b1961fee",
    },
    "settings-section-backup": {
        "title": "Backup",
        "body": """\
• **Clouds:** हर क्लाउड को कनेक्ट करें या उससे साइन आउट करें। देखें [[settings-account]]।
• **Where each folder goes:** कौन-सा फ़ोल्डर किस क्लाउड में जाता है। देखें [[settings-destination]]।

एक क्लाउड पर **main (free)** लिखा होता है: वह पहला क्लाउड जो आपने चुना था। वह हमेशा मुफ़्त है। बाकी क्लाउड के लिए **Pro** चाहिए: 30 दिन मुफ़्त (**Start 30-day free trial**), फिर एक बार की खरीदारी (**Unlock Pro**)। अपने-आप कोई शुल्क नहीं लिया जाता।""",
        "en_hash": "0db12d3ebcb633de",
    },
    "settings-account": {
        "title": "आपके क्लाउड और Sign out",
        "body": """\
हर क्लाउड अपना खाता (या **Not connected**) दिखाता है, साथ में **Sign out**, **Connect** या **Enter keys**।

## Backup folder
हर कनेक्ट किए गए क्लाउड के नीचे (Google Photos को छोड़कर): वह फ़ोल्डर जहाँ उसके बैकअप जाते हैं, **Change** के साथ। देखें [[dialog-destination]]।

## Sign out
पहले एक चेतावनी: उस क्लाउड में बैकअप बंद हो जाएँगे, कुछ भी डिलीट नहीं होता। अगर उस क्लाउड में ऐसी फ़ाइलें हैं जो फ़ोन पर पूरे साइज़ में नहीं हैं (ऑप्टिमाइज़ की गई या Archive की गई), तो वह पूछता है:
• **Restore them to the phone first:** उन सबको वापस लाता है, फिर साइन आउट करता है।
• **Leave them there:** साइन आउट करता है।
• **Cancel:** साइन इन ही रहता है।

फ़ोल्डर उसी क्लाउड से जुड़े रहते हैं और आपके दोबारा साइन इन करने तक इंतज़ार करते हैं। उन्हें कहीं और भेजने के लिए **Where each folder goes** बदलें।""",
        "en_hash": "788b2237bf1a173f",
    },
    "settings-destination": {
        "title": "Where each folder goes",
        "body": """\
हर टॉप-लेवल फ़ोल्डर (DCIM, Pictures, Movies), वह क्लाउड जिसमें वह जाता है, और उस क्लाउड में कहाँ।

• क्लाउड बदलने के लिए उस पर टैप करें। इसे सिर्फ़ तब बदला जा सकता है जब एक से ज़्यादा क्लाउड हों और Pro या ट्रायल हो।
• बदलाव का असर सिर्फ़ नई फ़ाइलों पर होता है। जो फ़ाइलें पहले भेजी जा चुकी हैं, वे वहीं रहती हैं जहाँ हैं।
• Pro के बिना किसी अतिरिक्त क्लाउड से जुड़ा फ़ोल्डर कुछ नहीं भेजता, और यह बताता है।""",
        "en_hash": "22adac22e50fbdf0",
    },
    "dialog-destination": {
        "title": "Where new backups go (Change डायलॉग)",
        "body": """\
**Change** से खुलता है, जो किसी क्लाउड के **Backup folder** के पास होता है, Settings में या सेटअप के दौरान उसके कार्ड पर।

• फ़ोल्डर का पाथ टाइप करें, या OneDrive के लिए **Browse...** इस्तेमाल करें।
• **Reset to default:** वापस **Samsung Gallery/DCIM** (OneDrive) या **GallerySync** (बाकी क्लाउड)।
• **Use this folder** सेव करता है; **Cancel** कुछ नहीं बदलता।

नए फ़ोल्डर में सिर्फ़ नई फ़ाइलें जाती हैं। जिनका बैकअप हो चुका है, वे जहाँ हैं वहीं रहती हैं। OneDrive पुराने फ़ोल्डर देखता रहता है, इसलिए कुछ भी दो बार नहीं भेजा जाता।""",
        "en_hash": "910e0378a5a8b208",
    },
    "dialog-folder-picker": {
        "title": "फ़ोल्डर चुनें (OneDrive पिकर)",
        "body": """\
OneDrive के लिए **Browse...** से खुलता है। आपके OneDrive फ़ोल्डर सूचीबद्ध करता है: खोलने के लिए टैप करें, वापस जाने के लिए **Up one folder**, चुनने के लिए **Use this folder**, नया बनाने के लिए **New folder**।""",
        "en_hash": "f45e3349352eea44",
    },
    "settings-section-albums": {
        "title": "Albums",
        "body": """\
GallerySync किन फ़ोल्डर पर नज़र रखता है, और फ़ोन से डिलीट करने पर क्लाउड की कॉपियों का क्या होता है। नए एल्बम हमेशा **Off** से शुरू होते हैं।""",
        "en_hash": "dad7459947f2d8e1",
    },
    "settings-folders": {
        "title": "Folders to back up",
        "body": """\
वे फ़ोल्डर जिनमें GallerySync देख सकता है।

• **Add a folder:** Android का फ़ोल्डर पिकर खोलता है।
• **Remove:** फ़ोल्डर चुनें, **Remove** दबाएँ, और पुष्टि करें।

इन फ़ोल्डरों के बाहर कुछ भी स्कैन या बदला नहीं जाता। किसी फ़ोल्डर को हटाने से कुछ नहीं खोता: उसे वापस जोड़ें और सब कुछ लौट आता है।""",
        "en_hash": "3789c04bb52baef5",
    },
    "dialog-remove-folder": {
        "title": "Stop watching this folder?",
        "body": """\
**Remove** चुने हुए फ़ोल्डरों पर नज़र रखना बंद करता है; **Cancel** कुछ नहीं बदलता।

जिनका बैकअप हो चुका है, उसमें से कुछ नहीं खोता। फ़ोल्डर वापस जोड़ने पर उसके एल्बम और इतिहास लौट आते हैं, और कुछ भी दो बार नहीं भेजा जाता।""",
        "en_hash": "5b84d0c4a6ec8a9a",
    },
    "settings-deletion": {
        "title": "जब आप इस फ़ोन से कोई फ़ोटो या वीडियो डिलीट करते हैं",
        "body": """\
• **Leave the Cloud copy** (डिफ़ॉल्ट): आपके क्लाउड से कभी कुछ नहीं हटाया जाता।
• **Ask me about the Cloud copy:** जब फ़ाइलें आपके फ़ोन से जाती हैं, तो एक विंडो पूछती है कि उनकी क्लाउड कॉपियों का क्या करना है। अगर सूचनाएँ अनुमत हैं, तो डिलीट करने के कुछ ही देर बाद आपको बताया भी जाता है। आपकी मर्ज़ी के बिना कुछ नहीं हटता। देखें [[deleted-files-window]]।

अभी सिर्फ़ OneDrive के लिए। दूसरे क्लाउड की कॉपियाँ कभी नहीं हटाई जातीं।""",
        "en_hash": "5fce34ecb857a3f6",
    },
    "deleted-files-window": {
        "title": "आपके फ़ोन से डिलीट की गई फ़ाइलें (ऐप के साथ खुलने वाली विंडो)",
        "body": """\
**Ask** के तहत, जब फ़ाइलें आपके फ़ोन से जा चुकी हों, तो यह Albums टैब से पहले खुलती है। शुरू में कुछ भी चुना हुआ नहीं होता।

## विंडो 1: एक कॉपी OneDrive में है
उन फ़ाइलों को चुनने के लिए दाएँ स्वाइप करें (या टैप करें) जिनकी OneDrive कॉपी हटनी चाहिए।
• **Remove N from OneDrive:** पुष्टि माँगता है, फिर उन कॉपियों को OneDrive के रीसायकल बिन में भेज देता है। जो फ़ाइलें चुनी नहीं गईं, वे OneDrive में रहती हैं। देखें [[dialog-remove-from-onedrive]]।
• **Keep all in OneDrive.**
• **Decide later.**

## विंडो 2: कोई कॉपी नहीं मिली
ये फ़ाइलें अब भी आपके फ़ोन के ट्रैश में हैं।
• **Back up N to OneDrive:** चुनी हुई फ़ाइलों को ट्रैश से भेजता है। कुछ भी डिलीट नहीं होता।
• **Leave all in the trash.**
• **Decide later.**

## ध्यान रखने की बातें
• जब तक आप फ़ैसला नहीं करते, अनिर्णीत फ़ाइलें सूची में रहती हैं।
• सूची में नहीं: जिन फ़ाइलों को Archive ने हटाया, जगह पर ही एडिट की गई फ़ोटो, और वे फ़ाइलें जो बिना किसी क्लाउड कॉपी के सीधे डिलीट हो गईं।
• अगर आपकी बैकअप हुई आधी से ज़्यादा फ़ाइलें एक साथ गायब होती दिखें, तो कुछ भी पेश नहीं किया जाता; इसे असली डिलीट के बजाय गड़बड़ स्कैन माना जाता है।
• Back वही करता है जो **Decide later** करता है।""",
        "en_hash": "4cf138e07d6f0844",
    },
    "dialog-remove-from-onedrive": {
        "title": "Remove files from OneDrive? (पुष्टि)",
        "body": """\
दिखाता है कि कितनी फ़ाइलें हैं और उनका साइज़ कितना है। वे OneDrive के रीसायकल बिन में जाती हैं, जिसे GallerySync कभी खाली नहीं करता।

**Remove from OneDrive** आगे बढ़ता है; **Cancel** कुछ तय नहीं करता। जो फ़ाइल फ़ोन पर वापस आ चुकी है, उसे छुआ नहीं जाता।""",
        "en_hash": "a5707f1ea8aad403",
    },
    "settings-section-sync": {
        "title": "Sync",
        "body": """\
**Sync** एल्बम की फ़ोटो और वीडियो कैसे ऑप्टिमाइज़ होते हैं। फ़ाइलें तभी बदलती हैं जब क्लाउड उन्हें जाँच ले। OneDrive, Google Drive, Dropbox, pCloud, IDrive e2 और Backblaze B2 के साथ काम करता है।""",
        "en_hash": "1147f4e959ac06bc",
    },
    "settings-optimise-photos": {
        "title": "Optimise photos",
        "body": """\
डिफ़ॉल्ट रूप से **Off**। चालू होने पर, Sync एल्बम की फ़ोटो छोटी कॉपियों से बदल दी जाती हैं: लंबे किनारे पर लगभग 2048 पिक्सेल, साइज़ में लगभग दसवाँ हिस्सा। वे एक छोटे क्लाउड बैज के साथ आपकी गैलरी में बनी रहती हैं। मूल फ़ाइलें क्लाउड में रहती हैं।

जो फ़ोटो पहले से काफ़ी छोटी हैं, और **Keep at full size** वाली फ़ाइलें अछूती रहती हैं। **Mode** तय करता है कि यह कब चले। देखें [[settings-optimise-mode]]।""",
        "en_hash": "dee027d401742ca5",
    },
    "settings-optimise-video": {
        "title": "Optimise video",
        "body": """\
डिफ़ॉल्ट रूप से **Off**। चालू होने पर **Mode**, **Older than** और **Compression** दिखते हैं।

Sync एल्बम के वीडियो छोटे करके दोबारा एनकोड किए जाते हैं। कुछ भी काटा नहीं जाता। मूल फ़ाइलें क्लाउड में रहती हैं।

## कौन-से वीडियो
Sync एल्बम में हों, verified in the Cloud हों, आपकी सेटिंग से पुराने हों, उस फ़ोल्डर में हों जिसका ऐक्सेस आपने दिया है, और पहले से ऑप्टिमाइज़ न हों।

## कब
• **Automatic:** बैकअप के बाद, सिर्फ़ चार्जिंग के दौरान।
• **Manual:** जब आप **Sync now** दबाते हैं।""",
        "en_hash": "09cf14026b288ef6",
    },
    "settings-optimise-mode": {
        "title": "Mode: Automatic or Manual",
        "body": """\
• **Automatic** (डिफ़ॉल्ट): Sync एल्बम की फ़ाइलें क्लाउड के कन्फ़र्म करते ही ऑप्टिमाइज़ हो जाती हैं। वीडियो चार्जर का इंतज़ार करता है।
• **Manual:** जब तक आप Albums टैब पर **Sync now** नहीं दबाते, कुछ नहीं होता।

जिन फ़ोल्डर का ऐक्सेस आपने दिया है उनके बाहर की फ़ोटो के लिए Android की पुष्टि चाहिए, इसलिए वे तब तक इंतज़ार करती हैं जब तक आप ऐप नहीं खोलते या **Sync now** नहीं दबाते।""",
        "en_hash": "2d55959b2e538398",
    },
    "settings-video-age": {
        "title": "Older than (video)",
        "body": """\
एक वीडियो कितना पुराना होना चाहिए ताकि वह ऑप्टिमाइज़ हो: **Straight away**, **1 hour**, **12 hours**, **1 day** या **1 week**। बैकअप में देरी नहीं होती।

**Straight away** में आज शूट की गई क्लिप भी शामिल हैं; किसी को एडिट करने पर वह छोटी कॉपी से शुरू होता है, जब तक आप उसे वापस नहीं ले आते।""",
        "en_hash": "b69444389e56afe8",
    },
    "settings-video-quality": {
        "title": "Compression (video)",
        "body": """\
• **High compression, 480p:** सबसे छोटी फ़ाइलें, लगभग 85% बचत।
• **Medium compression, 720p:** लगभग 75% बचत।
• **Low compression, 1080p:** लगभग 50% बचत।

सामान्य आँकड़े; नतीजे अलग हो सकते हैं।

## कंप्रेशन बढ़ाने के बाद
ऐप पूछता है **Re-optimise existing videos?** **Yes** उन वीडियो को फिर से आज़माने देता है जो पहले इतने छोटे थे कि और सिकुड़ नहीं सके। **No** उन्हें छोड़ देता है। जो वीडियो पहले ही ऑप्टिमाइज़ हो चुके हैं, उन्हें छुआ नहीं जाता।""",
        "en_hash": "b68338f9be222883",
    },
    "settings-section-restore": {
        "title": "Restore (Settings)",
        "body": """\
Restore टैब के लिए एक स्विच: **Show empty folders**।""",
        "en_hash": "58571cb29a96c3ed",
    },
    "settings-show-empty-folders": {
        "title": "Show empty folders",
        "body": """\
डिफ़ॉल्ट रूप से **Off**। चालू होने पर, Restore टैब हर OneDrive फ़ोल्डर की सूची दिखाता है, उन फ़ोल्डरों समेत जिनमें वापस लाने को कुछ नहीं है।""",
        "en_hash": "44bada9612e80a0d",
    },
    "settings-section-camera": {
        "title": "Camera Sync (Settings)",
        "body": """\
**Special settings for Camera**, डिफ़ॉल्ट रूप से बंद। चालू होने पर:
• **Camera folder:** वह फ़ोल्डर जिसमें आपका कैमरा सेव करता है। उसे सूची से चुनें, या **Other…**।
• **Select Photos/Videos older than**, **Photos**, **Videos:** उस फ़ोल्डर के ऑप्टिमाइज़ नियंत्रणों के डिफ़ॉल्ट ([[album-camera-optimise]])।
• **Video compression:** वहाँ ऑप्टिमाइज़ हुए वीडियो के लिए।

Camera folder में **Sync** मोड नहीं होता, और जब तक आप उसमें **Sync now** नहीं दबाते, कुछ ऑप्टिमाइज़ नहीं होता। ऊपर के Optimise स्विच उस पर असर नहीं डालते।""",
        "en_hash": "d596a9c99a8de79d",
    },
    "settings-section-archive": {
        "title": "Archive (Settings)",
        "body": """\
• **Only show files older than:** जहाँ से Archive टैब का फ़िल्टर शुरू होता है। देखें [[settings-archive-default-age]]।
• **Notify when files are ready to archive:** डिफ़ॉल्ट रूप से बंद। देखें [[settings-archive-notify]]।""",
        "en_hash": "7b4561ddc010537d",
    },
    "settings-archive-default-age": {
        "title": "Only show files older than (Settings)",
        "body": """\
जहाँ से Archive टैब का उम्र वाला फ़िल्टर शुरू होता है: **1 hour**, **1 day**, **1 week**, **1 month**, **1 year** या **All** (डिफ़ॉल्ट)। Archive टैब पर इसे बदलना सिर्फ़ उसी बार के लिए रहता है। देखें [[archive-age-filter]]।""",
        "en_hash": "215ff23fa51a5fca",
    },
    "settings-archive-notify": {
        "title": "Notify when files are ready to archive",
        "body": """\
डिफ़ॉल्ट रूप से **Off**। चालू होने पर, Archive के लिए तैयार फ़ाइलों के हर नए बैच पर आपको एक बार सूचना मिलती है। सिर्फ़ OneDrive की फ़ाइलें।

Albums टैब और बाहर निकलते समय की याद दिलाने वाली सूचना भी यह दिखाती हैं, इसलिए आप सिर्फ़ सूचनाओं पर निर्भर नहीं रहते। अगर सूचनाएँ ब्लॉक हैं, तो स्विच यह बताता है।""",
        "en_hash": "a0b5e2290fe1eaae",
    },
    "settings-about-cards": {
        "title": "Help & Feedback, और नीचे के लिंक कार्ड",
        "body": """\
## Help & Feedback
• **Rate us on Play Store:** GallerySync की लिस्टिंग खोलता है।
• **Bug Report:** कहाँ लिखना है, और आपके ऐप संस्करण, फ़ोन मॉडल और Android संस्करण के लिए **Copy details**।

## नीचे
• **Privacy Policy.**
• **Delete Account Info:** साइन आउट कैसे करें, ऐप का ऐक्सेस कैसे हटाएँ और उसका डेटा कैसे साफ़ करें।
• **Contact Info:** पता, **Copy address** के साथ।

पेज इंटरनेट से लोड होते हैं। अगर कोई लोड न हो, तो **Open in browser** इस्तेमाल करें।""",
        "en_hash": "8a454188917d719a",
    },
}
