package ca.phon.app;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

/**
 * Send message to custom message handler window using WM_USER message. A new launcher will
 * be created by the installer at bin/phon_uri_handler.exe which executes this program. If
 * Phon is not running (i.e., the message window is not found) it is opened with the uri
 * as an argument.
 */
public final class PhonURIMessageSender {

    private final static String USE_MSG = "Usage phon_uri_handler <phon:uri>";

    public static void main(String[] args) {
        if(args.length != 1) {
            System.err.println(USE_MSG);
            System.exit(1);
        }

        final String uri = args[0].trim();
        try {
            URI parsedURI = new URI(uri);
            sendURI(parsedURI);

            System.exit(0);
        } catch (IOException | URISyntaxException e) {
            System.err.println("Invalid URI " + uri + ", " + e.getMessage());
            System.exit(2);
        }
    }

    private static void sendURI(URI uri) throws IOException {
        WinDef.HWND hWnd = determineHWNDFromWindowClass(WindowsURIHandler.WINDOW_CLASS);
        if(hWnd == null) {
            openPhonWithURI(uri);
        } else {
            final UUID uuid = UUID.randomUUID();
            final long messageId = uuid.getLeastSignificantBits();

            final File uriRequestFile = WindowsURIHandler.uriRequestFileFromId(messageId);
            final PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(uriRequestFile), "UTF-8"));
            printWriter.write(uri.toString());
            printWriter.write("\r\n");
            printWriter.flush();
            printWriter.close();

            WinDef.LRESULT result = User32.INSTANCE.SendMessage(hWnd, WinUser.WM_USER, new WinDef.WPARAM(0),
                new WinDef.LPARAM(messageId));
            if(result.intValue() != 0) {
                throw new RuntimeException("Failed to send WM_USER message");
            }
        }
    }

    private static void openPhonWithURI(URI uri) throws IOException {
        String exeName = "Phon";
        if(VersionInfo.getInstance().getPreRelease() != null) {
            exeName += VersionInfo.getInstance().getPreRelease().split("\\.")[0];
        }
        exeName += ".exe";

        final ProcessBuilder pb = new ProcessBuilder(exeName, uri.toString());
        pb.start();
    }

    public static WinDef.HWND determineHWNDFromWindowClass(String windowClass) {
        CallBackFindWindowHandleByWindowclass cb = new CallBackFindWindowHandleByWindowclass(windowClass);
        User32.INSTANCE.EnumWindows(cb, null);
        return cb.getFoundHwnd();
    }

    private static class CallBackFindWindowHandleByWindowclass implements WinUser.WNDENUMPROC {

        private WinDef.HWND found;

        private String windowClass;

        public CallBackFindWindowHandleByWindowclass(String windowClass) {
            this.windowClass = windowClass;
        }

        @Override
        public boolean callback(WinDef.HWND hWnd, Pointer data) {

            char[] windowText = new char[512];
            User32.INSTANCE.GetClassName(hWnd, windowText, windowText.length);
            String className = Native.toString(windowText);

            if (windowClass.equalsIgnoreCase(className)) {
                // Found handle. No determine root window...
                WinDef.HWND hWndAncestor = User32.INSTANCE.GetAncestor(hWnd, User32.GA_ROOTOWNER);
                found = hWndAncestor;
                return false;
            }
            return true;
        }

        public WinDef.HWND getFoundHwnd() {
            return this.found;
        }

    }

}
