package com.gmazzoni.cordova;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.zip.GZIPInputStream;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import com.googlecode.tesseract.android.TessBaseAPI;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import android.content.Context;

public class TesseractPlugin extends CordovaPlugin {
    public static final int PERMISSION_DENIED_ERROR = 20;
    public static final int SAVE_TO_EXTERNAL_STORAGE = 1;

    public static final String DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/OCRFolder/";
    private static final String TAG = "TesseractPlugin";
    private String lang = "por";
    public CallbackContext callbackContext;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;

        try {
            String language = args.getString(0);

            String result = null;
            Log.v(TAG, "Action: " + action);
            if (action.equals("recognizeText")) {
                String imageData = args.getString(1);
                result = recognizeText(imageData, language);
            } else {
                result = loadLanguage(language);
            }

            Log.v(TAG, "Result: " + result);
            this.echo(result, callbackContext);
            return true;
        } catch (Exception e) {
            Log.v(TAG, "Exception in Execute:" + e.getMessage());
            callbackContext.error(e.getMessage());
            return false;
        }
    }


    private void echo(String result, CallbackContext callbackContext) {
        if (result != null && result.length() > 0) {
            callbackContext.success(result);
        } else {
            callbackContext.error("Expected one non-empty string argument.");
        }
    }

    public String recognizeText(String imageData, String language) {
        Log.v(TAG, "Starting process to recognize text in photo.");

        byte[] decodedString = Base64.decode(imageData, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

        Log.v(TAG, "Before baseApi");

        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.setDebug(true);
        baseApi.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO_OSD);
        baseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST,"0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ@&#'.,-_/()[]{}:+àâæäãçéèêëeîïìíñôœöòóõûùüúÿÀÂÆÄÃÇÉÈÊËEÎÏÌÍÑÔŒÖÒÓÕÛÙÜÚŸ");


        baseApi.init(DATA_PATH, language);
        baseApi.setImage(bitmap);

        String recognizedText = "";
        recognizedText = baseApi.getUTF8Text();

        baseApi.end();

        Log.v(TAG, "Recognized Text: " + recognizedText);

        String jsonResult = extractData(recognizedText);

        return jsonResult;
    }

    private String extractData(String recognizedText)
    {
        String data = "{\"urls\": [], \"phones\": [], \"addresses\": [], \"raw_data\": ";

        String escapedRawData = JSONObject.quote(recognizedText);
        data += escapedRawData;
        data += "}";

        return data;
    }

    public String loadLanguage(String language) {
        this.lang = language;
        Log.v(TAG, "Starting process to load OCR language file.");
        if(!PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            PermissionHelper.requestPermission(this, SAVE_TO_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        else
        {
            String[] paths = new String[] { DATA_PATH, DATA_PATH + "tessdata/" };
            for (String path : paths) {
                File dir = new File(path);
                if (!dir.exists()) {
                    if (!dir.mkdirs()) {
                        Log.v(TAG, "Error: Creation of directory " + path + " on sdcard failed");
                        return "Error: Creation of directory " + path + " on sdcard failed";
                    } else {
                        Log.v(TAG, "Directory created " + path + " on sdcard");
                    }
                }
            }

            if (language != null && language != "") {
                lang = language;
            }

            if (!(new File(DATA_PATH + "tessdata/" + lang + ".traineddata")).exists()) {
                boolean downloadData = preferences.getBoolean("DownloadTesseractData", true);
                if(downloadData) {
                    DownloadAndCopy job = new DownloadAndCopy();
                    job.execute(lang);
                }
                else {
                    CopyFromAssets job = new CopyFromAssets(lang, this.cordova.getActivity().getApplicationContext());
                    job.execute(lang);
                }
            }
        }
        return "Ok";
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
                return;
            }
        }
        switch (requestCode) {
            case SAVE_TO_EXTERNAL_STORAGE:
                this.loadLanguage(this.lang);
                break;
        }
    }

    private class CopyFromAssets extends AsyncTask<String, Void, String> {
        String lang;
        Context ctx;

        CopyFromAssets(String lang, Context ctx) {
            this.lang = lang;
            this.ctx = ctx;
        }

        @Override
        protected String doInBackground(String[] params) {

            try {
                InputStream input;
                input = ctx.getAssets().open("traineddata/" + this.lang + ".traineddata");
                OutputStream out = new FileOutputStream(DATA_PATH + "tessdata/" + this.lang + ".traineddata");

                try {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = input.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    out.flush();
                } finally {
                    out.close();
                    input.close();
                }

            } catch (IOException e) {
                Log.e(TAG, "Unable to copy " + this.lang + ".traineddata " + e.toString());
            }

            return "Copied " + this.lang + ".traineddata";
        }

        @Override
        protected void onPostExecute(String message) {
            //process message
            Log.v(TAG, "Copying of traineddata done! Nothing else to do.");
        }
    }

    private class DownloadAndCopy extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String[] params) {
            // do above Server call here
            try {
                Log.v(TAG, "Downloading " + lang + ".traineddata");
                URL url = new URL("https://cdn.rawgit.com/naptha/tessdata/gh-pages/3.02/"+lang+".traineddata.gz");
                GZIPInputStream gzip = new GZIPInputStream(url.openStream());
                Log.v(TAG, "Downloaded and unziped " + lang + ".traineddata");

                OutputStream out = new FileOutputStream(DATA_PATH
                        + "tessdata/" + lang + ".traineddata");

                byte[] buf = new byte[1024];
                int len;
                while ((len = gzip.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }

                gzip.close();
                out.close();

                Log.v(TAG, "Copied " + lang + ".traineddata");
            } catch (IOException e) {
                Log.e(TAG, "Unable to copy " + lang + ".traineddata " + e.toString());
            }

            return "Copied " + lang + ".traineddata";
        }

        @Override
        protected void onPostExecute(String message) {
            //process message
            Log.v(TAG, "Download and copy done! Nothing else to do.");
        }
    }
}
