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
        InputStream in = getResources().openRawResource(
                R.raw.sample_attachment_image1);
        FileOutputStream out;
        try {
            out = new FileOutputStream(getFilesDir()
                    + "/sample_attachment_image1.jpg");
            byte[] buff = new byte[1024];
            int read = 0;

            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
            out.close();
            in.close();

            in = getResources().openRawResource(R.raw.doc);
            out = new FileOutputStream(getFilesDir() + "/doc.json");
            buff = new byte[1024];
            read = 0;
            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
            out.close();
            in.close();

            in = getResources().openRawResource(R.raw.foo);
            out = new FileOutputStream(getFilesDir() + "/foo.txt");
            buff = new byte[1024];
            read = 0;
            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
            out.close();
            in.close();

            in = getResources().openRawResource(R.raw.bar);
            out = new FileOutputStream(getFilesDir() + "/bar.txt");
            buff = new byte[1024];
            read = 0;
            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
            out.close();
            in.close();

            in = getResources().openRawResource(R.raw.sample_attachment_image2);
            out = new FileOutputStream(getFilesDir()
                    + "/sample_attachment_image2.jpg");
            buff = new byte[1024];
            read = 0;
            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
            out.close();
            in.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(in);
        }

        finish();

    }
}