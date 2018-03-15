# Upgrade test tool from 1.x to 2.0

1. Default codes uses CBL 1.4.1 and creates one document. Whenever the application starts, it updates the document.
2. To change CBL 2.0.0
- update app/build.gradle, please follow TODO comment in the file
- update MainActivity.java,  please follow TODO comment in thte file
3. Luanch launch app.
- If database upgrade is successful, you could see following logs in log cat
```
I/LiteCore: Upgrader upgrading db </data/user/0/com.couchbase.upgradetest/files/upgrade.cblite2/>; creating new db at </data/user/0/com.couchbase.upgradetest/files/upgrade.cblite2_TEMP/>
I/LiteCore: Upgrading CBL 1.x database </data/user/0/com.couchbase.upgradetest/files/upgrade.cblite2/>, user_version=102)
I/LiteCore: Importing doc 'doc_upgrade'
I/LiteCore:         ...rev 3-f5118a3c9653ba9b21197d35c5029484
I/LiteCore:         ...rev 2-05f7a7c9fb75865b041c1b441feee14e
I/LiteCore:         ...rev 1-3fdf2109cb5287ee38b25f61d6d9d9cf
I/LiteCore: Upgrader finished
I/LiteCore: Finished async delete of replaced </data/user/0/com.couchbase.upgradetest/cache/CBL_Obsolete-ddZtXD/upgrade.cblite2>
```
- After upgrade completes, app update the doc.