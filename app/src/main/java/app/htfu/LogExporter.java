// SPDX-License-Identifier: GPL-3.0-or-later
// HTFU - https://htfu.app/
package app.htfu;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
public class LogExporter {
    private static final String TAG = "HTFU";

    static String safeName(String str) {
        if (str == null || str.isEmpty()) {
            return "share.json";
        }
        return str.replaceAll("[\\\\/:*?\"<>|]", "-");
    }

    public static void shareJson(MainActivity mainActivity, String str, String str2, String str3, String str4) {
        try {
            File file = new File(mainActivity.getCacheDir(), "exports");
            if (!file.exists()) {
                file.mkdirs();
            }
            File file2 = new File(file, safeName(str2));
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            fileOutputStream.write(str.getBytes("UTF-8"));
            fileOutputStream.close();
            Uri parse = Uri.parse("content://app.htfu.export/" + file2.getName());
            Intent intent = new Intent("android.intent.action.SEND");
            intent.setType(file2.getName().endsWith(".csv") ? "text/csv" : "application/json");
            intent.putExtra("android.intent.extra.STREAM", parse);
            if (str4 == null || str4.isEmpty()) {
                str4 = str3;
            }
            if (str4 != null && !str4.isEmpty()) {
                intent.putExtra("android.intent.extra.TEXT", str4);
                intent.putExtra("android.intent.extra.SUBJECT", str4);
            }
            intent.addFlags(1);
            if (str3 == null) {
                str3 = "Share";
            }
            mainActivity.startActivity(Intent.createChooser(intent, str3));
        } catch (Exception e) {
            Log.e(TAG, "shareJson failed", e);
            Toast.makeText(mainActivity, "Could not share.", 0).show();
        }
    }

    public static void share(MainActivity mainActivity, String str) {
        try {
            if (str == null) {
                str = "[]";
            }
            JSONArray jSONArray = new JSONArray(str);
            File file = new File(mainActivity.getCacheDir(), "exports");
            if (!file.exists()) {
                file.mkdirs();
            }
            File file2 = new File(file, "htfu-log.xlsx");
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            fileOutputStream.write(xlsx(jSONArray));
            fileOutputStream.close();
            Uri parse = Uri.parse("content://app.htfu.export/" + file2.getName());
            Intent intent = new Intent("android.intent.action.SEND");
            intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            intent.putExtra("android.intent.extra.STREAM", parse);
            intent.putExtra("android.intent.extra.SUBJECT", "HTFU log");
            intent.addFlags(1);
            mainActivity.startActivity(Intent.createChooser(intent, "Export log"));
        } catch (Exception e) {
            Log.e(TAG, "export failed", e);
            Toast.makeText(mainActivity, "Could not export the log.", 0).show();
        }
    }

    public static void backup(final MainActivity mainActivity, String str, String str2) {
        if (str2 == null) {
            str2 = "htfu-backup.json";
        }
        try {
            String safeName = safeName(str2);
            if (str == null) {
                str = "{}";
            }
            mainActivity.pendingBackupJson = str;
            final MainActivity ma0 = mainActivity; final String s0 = str;
            new Thread(new Runnable() {
                @Override
                public final void run() {
                    LogExporter.lambda$backup$0(ma0, s0);
                }
            }).start();
            Intent intent = new Intent("android.intent.action.CREATE_DOCUMENT");
            intent.addCategory("android.intent.category.OPENABLE");
            intent.setType("application/json");
            intent.putExtra("android.intent.extra.TITLE", safeName);
            mainActivity.startActivityForResult(intent, 45);
        } catch (Exception e) {
            Log.e(TAG, "backup failed", e);
            Toast.makeText(mainActivity, "Could not create backup.", 0).show();
        }
    }

    public static /* synthetic */ void lambda$backup$0(MainActivity mainActivity, String str) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(new File(mainActivity.getCacheDir(), "pending-backup.json"));
            fileOutputStream.write(str.getBytes("UTF-8"));
            fileOutputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "cache backup failed", e);
        }
    }

    static byte[] xlsx(JSONArray jSONArray) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
        put(zipOutputStream, "[Content_Types].xml", contentTypes());
        put(zipOutputStream, "_rels/.rels", rootRels());
        put(zipOutputStream, "xl/workbook.xml", workbook());
        put(zipOutputStream, "xl/_rels/workbook.xml.rels", workbookRels());
        put(zipOutputStream, "xl/worksheets/sheet1.xml", sheet(jSONArray));
        zipOutputStream.close();
        return byteArrayOutputStream.toByteArray();
    }

    static void put(ZipOutputStream zipOutputStream, String str, String str2) throws Exception {
        zipOutputStream.putNextEntry(new ZipEntry(str));
        zipOutputStream.write(str2.getBytes("UTF-8"));
        zipOutputStream.closeEntry();
    }

    static String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>";
    }

    static String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>";
    }

    static String workbook() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Log\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
    }

    static String workbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>";
    }

    static String sheet(JSONArray jSONArray) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        int i = 7;
        sb.append(row(1, new String[]{"Date", "Plan", "Session", "Detail", "Total", "Score", "Kind"}, null));
        int i2 = 0;
        while (i2 < jSONArray.length()) {
            JSONObject jSONObject = jSONArray.getJSONObject(i2);
            String[] strArr = new String[i];
            strArr[0] = jSONObject.optString("date");
            strArr[1] = jSONObject.optString("plan");
            strArr[2] = jSONObject.optString("session");
            strArr[3] = jSONObject.optString("detail");
            strArr[4] = null;
            strArr[5] = jSONObject.optString("score");
            strArr[6] = jSONObject.optString("kind");
            Integer[] numArr = new Integer[i];
            numArr[0] = null;
            numArr[1] = null;
            numArr[2] = null;
            numArr[3] = null;
            numArr[4] = Integer.valueOf(jSONObject.optInt("total"));
            numArr[5] = null;
            numArr[6] = null;
            sb.append(row(i2 + 2, strArr, numArr));
            i2++;
            i = 7;
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    static String row(int i, String[] strArr, Integer[] numArr) {
        StringBuilder sb = new StringBuilder();
        sb.append("<row r=\"").append(i).append("\">");
        String[] strArr2 = {"A", "B", "C", "D", "E", "F", "G"};
        for (int i2 = 0; i2 < 7; i2++) {
            sb.append("<c r=\"").append(strArr2[i2]).append(i).append("\"");
            if (numArr == null || numArr[i2] == null) {
                sb.append(" t=\"inlineStr\"><is><t>").append(esc(strArr == null ? "" : strArr[i2])).append("</t></is></c>");
            } else {
                sb.append("><v>").append(numArr[i2]).append("</v></c>");
            }
        }
        sb.append("</row>");
        return sb.toString();
    }

    static String esc(String str) {
        return str == null ? "" : str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
