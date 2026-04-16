package tool.xfy9326.floatpicture.Methods;

import android.content.Context;
import android.net.Uri;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Objects;

public class CodeMethods {

    private static final char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public static String unicodeEncode(String str) {
        if (str != null) {
            StringBuilder unicode = new StringBuilder();
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                unicode.append("\\u").append(Integer.toHexString(c));
            }
            str = unicode.toString();
        }
        return str;
    }

    public static String unicodeDecode(String str) {
        if (str != null) {
            StringBuilder string = new StringBuilder();
            String[] hex = str.split("\\\\u");
            for (int i = 1; i < hex.length; i++) {
                int data = Integer.parseInt(hex[i], 16);
                string.append((char) data);
            }
            str = string.toString();
        }
        return str;
    }

    static String getFileMD5String(Context context, Uri uri) {
        try (InputStream inputStream = Objects.requireNonNull(context.getContentResolver().openInputStream(uri))) {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                messageDigest.update(buffer, 0, read);
            }
            return bufferToHex(messageDigest.digest());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String bufferToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            appendHexPair(b, sb);
        }
        return sb.toString();
    }

    private static void appendHexPair(byte bt, StringBuilder sb) {
        char c0 = hexDigits[(bt & 0xf0) >> 4];
        char c1 = hexDigits[bt & 0xf];
        sb.append(c0);
        sb.append(c1);
    }

}
