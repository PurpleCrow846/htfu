// SPDX-License-Identifier: GPL-3.0-or-later
// HTFU - https://htfu.app/
package app.htfu;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.util.List;

/* loaded from: classes.dex */
public class ExportProvider extends ContentProvider {
    static final String AUTHORITY = "app.htfu.export";

    /**
     * 2.14: an export file must resolve (after "..", "%2F" and symlinks) to a direct child of
     * cache/exports. Anything else returns null, so a crafted URI can't reach other app files.
     */
    static File exportFile(Context context, Uri uri) {
        try {
            return safeChild(new File(context.getCacheDir(), "exports"), uri.getLastPathSegment());
        } catch (Exception e) {
            return null;
        }
    }

    /** Pure-Java part of exportFile (unit-tested off-device). */
    static File safeChild(File base, String name) {
        try {
            if (name == null || name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.equals(".") || name.equals("..")) return null;
            File dir = base.getCanonicalFile();
            File f = new File(dir, name).getCanonicalFile();
            File parent = f.getParentFile();
            if (parent == null || !parent.equals(dir)) return null;
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    /** 2.12: content://AUTHORITY/photos/NAME is a camera capture in cache/exports/photos; anything else is an export file. */
    static File photoFile(Context context, Uri uri) {
        List<String> seg = uri.getPathSegments();
        if (seg == null || seg.size() != 2 || !"photos".equals(seg.get(0))) {
            return null;
        }
        String name = seg.get(1);
        if (name == null || !name.matches("cam-[0-9]+\\.jpg")) {
            return null;
        }
        return new File(context.getCacheDir(), "exports/photos/" + name);
    }

    @Override // android.content.ContentProvider
    public boolean onCreate() {
        return true;
    }

    @Override // android.content.ContentProvider
    public ParcelFileDescriptor openFile(Uri uri, String str) {
        try {
            File photo = photoFile(getContext(), uri);
            if (photo != null) {
                // the camera app writes the capture here ("w"/"rw"/"rwt"); the WebView then reads it
                int mode = (str == null || "r".equals(str)) ? ParcelFileDescriptor.MODE_READ_ONLY : ParcelFileDescriptor.parseMode(str);
                return ParcelFileDescriptor.open(photo, mode);
            }
            File export = exportFile(getContext(), uri);
            if (export == null) return null;
            return ParcelFileDescriptor.open(export, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Exception e) {
            return null;
        }
    }

    @Override // android.content.ContentProvider
    public Cursor query(Uri uri, String[] strArr, String str, String[] strArr2, String str2) {
        try {
            File f = photoFile(getContext(), uri);
            if (f == null) {
                return null; // exports: unchanged from 2.11
            }
            String[] cols = strArr != null ? strArr : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor c = new MatrixCursor(cols, 1);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) {
                    row[i] = f.getName();
                } else if (OpenableColumns.SIZE.equals(cols[i])) {
                    row[i] = Long.valueOf(f.length());
                }
            }
            c.addRow(row);
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    @Override // android.content.ContentProvider
    public String getType(Uri uri) {
        if (photoFile(getContext(), uri) != null) {
            return "image/jpeg";
        }
        String lastPathSegment = uri.getLastPathSegment();
        if (lastPathSegment != null && lastPathSegment.endsWith(".csv")) return "text/csv";
        return (lastPathSegment == null || !lastPathSegment.endsWith(".json")) ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "application/json";
    }

    @Override // android.content.ContentProvider
    public Uri insert(Uri uri, ContentValues contentValues) {
        return null;
    }

    @Override // android.content.ContentProvider
    public int delete(Uri uri, String str, String[] strArr) {
        return 0;
    }

    @Override // android.content.ContentProvider
    public int update(Uri uri, ContentValues contentValues, String str, String[] strArr) {
        return 0;
    }
}
