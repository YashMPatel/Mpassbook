package com.example.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.activity.result.ActivityResult;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
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
    public void saveReport(PluginCall call) {
        String title = call.getString("title");
        String filename = call.getString("filename");
        JSONArray lines;
        try {
            lines = call.getData().getJSONArray("lines");
        } catch (JSONException exception) {
            call.reject("PDF report lines are required.", exception);
            return;
        }
        if (title == null || filename == null) {
            call.reject("PDF report title and filename are required.");
            return;
        }

        Intent intent = createDocumentIntent("application/pdf", filename);
        startActivityForResult(call, intent, "saveReportResult");
    }

    @ActivityCallback
    private void saveBackupResult(PluginCall call, ActivityResult result) {
        if (call == null) {
            return;
        }
        Uri uri = getResultUri(call, result);
        if (uri == null) {
            return;
        }

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

    @ActivityCallback
    private void saveReportResult(PluginCall call, ActivityResult result) {
        if (call == null) {
            return;
        }
        Uri uri = getResultUri(call, result);
        if (uri == null) {
            return;
        }

        String title = call.getString("title");
        JSONArray lines;
        try {
            lines = call.getData().getJSONArray("lines");
        } catch (JSONException exception) {
            call.reject("PDF report lines are no longer available.", exception);
            return;
        }
        if (title == null) {
            call.reject("PDF report title is no longer available.");
            return;
        }

        try (OutputStream output = getContext().getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                call.reject("Could not open the selected PDF location.");
                return;
            }
            writeReportPdf(title, lines, output);
        } catch (IOException exception) {
            call.reject("Could not create the PDF report.", exception);
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
        if (call == null) {
            return;
        }
        Uri uri = getResultUri(call, result);
        if (uri == null) {
            return;
        }

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

    private void writeReportPdf(String title, JSONArray reportLines, OutputStream output) throws IOException {
        PdfDocument document = new PdfDocument();
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        titlePaint.setTextSize(18);
        Paint normalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        normalPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        normalPaint.setTextSize(10);

        int pageWidth = 595;
        int pageHeight = 842;
        int margin = 40;
        float lineHeight = 16;
        int pageNumber = 1;
        PdfDocument.Page page = document.startPage(
            new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        );
        Canvas canvas = page.getCanvas();
        float y = margin;

        try {
            List<String> lines = new ArrayList<>();
            lines.add(title);
            for (int index = 0; index < reportLines.length(); index++) {
                lines.add(reportLines.optString(index, ""));
            }

            for (int index = 0; index < lines.size(); index++) {
                Paint paint = index == 0 ? titlePaint : normalPaint;
                for (String wrappedLine : wrapLine(lines.get(index), paint, pageWidth - margin * 2)) {
                    if (y + lineHeight > pageHeight - margin) {
                        document.finishPage(page);
                        pageNumber++;
                        page = document.startPage(
                            new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        );
                        canvas = page.getCanvas();
                        y = margin;
                    }
                    canvas.drawText(wrappedLine, margin, y + paint.getTextSize(), paint);
                    y += index == 0 ? 26 : lineHeight;
                }
            }
            document.finishPage(page);
            document.writeTo(output);
        } finally {
            document.close();
        }
    }

    private List<String> wrapLine(String text, Paint paint, float maxWidth) {
        List<String> wrapped = new ArrayList<>();
        if (text.isEmpty()) {
            wrapped.add("");
            return wrapped;
        }

        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && paint.measureText(candidate) > maxWidth) {
                wrapped.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            wrapped.add(line.toString());
        }
        return wrapped;
    }

}
