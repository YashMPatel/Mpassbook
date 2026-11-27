package com.example.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.DocumentsContract;
import android.webkit.WebView;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@CapacitorPlugin(name = "Backup")
public class BackupPlugin extends Plugin {
    private static final String DOWNLOADS_URI =
        "content://com.android.externalstorage.documents/document/primary%3ADownload";

    @PluginMethod
    public void saveBackup(PluginCall call) {
        String content = call.getString("content");
        if (content == null) {
            call.reject("Backup content is required.");
            return;
        }

        String filename = call.getString("filename");
        if (filename == null || filename.trim().isEmpty()) {
            filename = "Mpassbook_Backup_" + currentDate() + ".json";
        }
        Intent intent = createDocumentIntent("application/json", filename);
        startActivityForResult(call, intent, "saveBackupResult");
    }

    @PluginMethod
    public void printHtmlReport(PluginCall call) {
        String title = call.getString("title", "Mpassbook_Report_" + currentDate());
        Activity activity = getActivity();
        WebView webView = getBridge().getWebView();

        if (activity == null || webView == null) {
            call.reject("WebView is not available for printing.");
            return;
        }

        activity.runOnUiThread(() -> {
            PrintManager printManager = (PrintManager) activity.getSystemService(Context.PRINT_SERVICE);
            if (printManager == null) {
                call.reject("Print service unavailable.");
                return;
            }
            PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(title);
            printManager.print(title, adapter, new PrintAttributes.Builder().build());
            call.resolve();
        });
    }

    @ActivityCallback
    private void saveBackupResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Uri uri = getResultUri(call, result);
        if (uri == null) return;

        String content = call.getString("content");
        if (content == null) {
            call.reject("Backup content is no longer available.");
            return;
        }

        try (OutputStream output = getContext().getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                call.reject("Could not open the selected backup location.");
                return;
            }
            output.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException exception) {
            call.reject("Could not save the backup file.", exception);
            return;
        }
        JSObject response = new JSObject();
        response.put("cancelled", false);
        call.resolve(response);
    }

    @PluginMethod
    public void pickBackup(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(DOWNLOADS_URI));
        startActivityForResult(call, intent, "pickBackupResult");
    }

    @ActivityCallback
    private void pickBackupResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Uri uri = getResultUri(call, result);
        if (uri == null) return;

        try (InputStream input = getContext().getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                call.reject("Could not open the selected backup file.");
                return;
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }

            JSObject response = new JSObject();
            response.put("cancelled", false);
            response.put("content", output.toString(java.nio.charset.StandardCharsets.UTF_8.name()));
            call.resolve(response);
        } catch (IOException exception) {
            call.reject("Could not read the selected backup file.", exception);
        }
    }

    private Intent createDocumentIntent(String mimeType, String filename) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(DOWNLOADS_URI));
        return intent;
    }

    private Uri getResultUri(PluginCall call, ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            JSObject response = new JSObject();
            response.put("cancelled", true);
            call.resolve(response);
            return null;
        }

        Uri uri = result.getData().getData();
        if (uri == null) {
            call.reject("Could not access the selected file.");
        }
        return uri;
    }

    private String currentDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }
}