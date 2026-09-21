package com.yt8492.asmrplayer.ui.preview;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;

/** テストAPK単独の別プロセスで動くため、対象APK内のKotlinランタイムに依存させない。 */
public class PreviewPipeProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/pdf"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if ("denied".equals(uri.getLastPathSegment())) throw new SecurityException("検証用のアクセス拒否");
        final ParcelFileDescriptor[] pipe;
        try { pipe = ParcelFileDescriptor.createPipe(); }
        catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
        new Thread(() -> {
            try (ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                PdfDocument pdf = new PdfDocument();
                try {
                    PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(600, 800, 0).create());
                    page.getCanvas().drawColor(Color.MAGENTA);
                    pdf.finishPage(page);
                    pdf.writeTo(output);
                } finally { pdf.close(); }
            } catch (IOException ignored) {
                // 最初のPdfRendererはパイプを拒否して閉じるため、書き込み失敗は正常。
            }
        }, "preview-test-pipe").start();
        return pipe[0];
    }
}
