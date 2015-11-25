package com.couchbase.lite.util;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipInputStream;

/**
 * Created by pasin on 11/22/15.
 */
public class ICUUtils {
    public static String getICUDatabasePath(Context context) {
        if (new File("/system/usr/icu/icudt53l.dat").exists())
            return "/system/usr";
        else {
            File destDir = context.getFilesDir();
            try {
                loadICUData(context, destDir);
            } catch (IOException e) {
                Log.e(Log.TAG_DATABASE, "Cannot load ICU database file", e);
                return null;
            }
            return destDir.getAbsolutePath();
        }
    }

    private static void loadICUData(Context context, File destDir) throws IOException {
        OutputStream out = null;
        ZipInputStream in = null;
        File icuDir = new File(destDir, "icu");
        File icuDataFile = new File(icuDir, "icudt53l.dat");
        try {
            if (!icuDir.exists()) icuDir.mkdirs();
            if (!icuDataFile.exists()) {
                in = new ZipInputStream(context.getAssets().open("icudt53l.zip"));
                in.getNextEntry();
                out =  new FileOutputStream(icuDataFile);
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        } catch (IOException e) {
            if (icuDataFile.exists())
                icuDataFile.delete();
            throw e;
        } finally {
            if (in != null)
                in.close();
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }
}
