package com.couchbase.touchdb.testapp;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

import android.app.Activity;
import android.os.Bundle;

public class CopySampleAttachmentsActivity extends Activity {

    public static final String TAG = "CopySampleAttachmentsActivity";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        InputStream in = getResources().openRawResource(R.raw.sample_attachment_image1);
        InputStream in2 = (InputStream) getResources().openRawResource(R.raw.sample_attachment_image2);
        FileOutputStream out;
        FileOutputStream out2;
        try {
            out = new FileOutputStream(getFilesDir()
                    + "/sample_attachment_image1.jpg");
            byte[] buff = new byte[1024];
            int read = 0;

            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
            out.close();

            out2 = new FileOutputStream(getFilesDir()
                    + "/sample_attachment_image2.jpg");
            byte[] buff2 = new byte[1024];
            int read2 = 0;

            while ((read2 = in2.read(buff2)) > 0) {
                out2.write(buff2, 0, read2);
            }
            out2.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(in2);
        }

        finish();

    }
}