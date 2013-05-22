/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.cblite.support;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import android.util.Log;

import com.couchbase.cblite.CBLDatabase;

public class FileDirUtils {

    public static boolean removeItemIfExists(String path) {
        File f = new File(path);
        return f.delete() || !f.exists();
    }

    public static boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child);
            }
        }

        boolean result = fileOrDirectory.delete() || !fileOrDirectory.exists();
        return result;
    }

    public static String getDatabaseNameFromPath(String path) {
        int lastSlashPos = path.lastIndexOf("/");
        int extensionPos = path.lastIndexOf(".");
        if(lastSlashPos < 0 || extensionPos < 0 || extensionPos < lastSlashPos) {
            Log.e(CBLDatabase.TAG, "Unable to determine database name from path");
            return null;
        }
        return path.substring(lastSlashPos + 1, extensionPos);
    }

    public static void copyFile(File sourceFile, File destFile) throws IOException {
        if(!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        }
        finally {
            if(source != null) {
                source.close();
            }
            if(destination != null) {
                destination.close();
            }
        }
    }

    public static void copyFolder(File src, File dest)
            throws IOException{

            if(src.isDirectory()){

                //if directory not exists, create it
                if(!dest.exists()){
                   dest.mkdir();
                }

                //list all the directory contents
                String files[] = src.list();

                for (String file : files) {
                   //construct the src and dest file structure
                   File srcFile = new File(src, file);
                   File destFile = new File(dest, file);
                   //recursive copy
                   copyFolder(srcFile,destFile);
                }

            }else{
                copyFile(src, dest);
            }
        }

}
