package org.xbmc.kore.ui.sections.localfile;


import android.content.Context;
import android.net.wifi.WifiManager;

import org.xbmc.kore.utils.LogUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static android.content.Context.WIFI_SERVICE;


public class HttpApp extends NanoHTTPD {

    private HttpApp(Context applicationContext, int port) throws IOException {
        super(port);
        this.applicationContext = applicationContext;
        this.localFileLocationList = new LinkedList<>();
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }

    private Context applicationContext;
    private LinkedList<LocalFileLocation> localFileLocationList = null;
    private int currentIndex;

    @Override
    public Response serve(IHTTPSession session) {

        Map<String, List<String>> params = session.getParameters();
        if (localFileLocationList == null) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "", "");
        }

        if (!params.containsKey("number")) {
            return null;
        }

        int file_number = Integer.parseInt(params.get("number").get(0));

        FileInputStream fis = null;
        LocalFileLocation localFileLocation = localFileLocationList.get(file_number);
        try {
            fis = new FileInputStream(localFileLocation.fullPath);
        } catch (FileNotFoundException e) {
            LogUtils.LOGW(LogUtils.makeLogTag(HttpApp.class), e.toString());
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "", "");
        }
        String mimeType = localFileLocation.getMimeType();
        return newChunkedResponse(Response.Status.OK, mimeType, fis);
    }

    public void addLocalFilePath(LocalFileLocation localFileLocation) {
        if (localFileLocationList.contains(localFileLocation)) {
            // Path already exists, get its index:
            currentIndex = localFileLocationList.indexOf(localFileLocation);
        } else {
            this.localFileLocationList.add(localFileLocation);
            currentIndex = localFileLocationList.size() - 1;
        }
    }

    private String getIpAddress() throws UnknownHostException {
        WifiManager wm = (WifiManager) applicationContext.getSystemService(WIFI_SERVICE);
        byte[] byte_address = BigInteger.valueOf(wm.getConnectionInfo().getIpAddress()).toByteArray();
        // Reverse `byte_address`:
        for (int i = 0; i < byte_address.length/2; i++) {
            byte temp = byte_address[i];
            int j = byte_address.length - i - 1;
            if (j < 0)
                break;
            byte_address[i] = byte_address[j];
            byte_address[j] = temp;
        }
        InetAddress inet_address = InetAddress.getByAddress(byte_address);
        String ip = inet_address.getHostAddress();
        return ip;
    }

    public String getLinkToFile() {
        String ip = null;
        try {
            ip = getIpAddress();
        } catch (UnknownHostException uhe) {
            return null;
        }
        try {
            if (!isAlive())
                start();
        } catch (IOException ioe) {
            LogUtils.LOGE(LogUtils.makeLogTag(HttpApp.class), ioe.getMessage());
        }
        return "http://" + ip + ":" + getListeningPort() + "/" + localFileLocationList.get(currentIndex).fileName + "?number=" + currentIndex;
    }

    private static HttpApp http_app = null;

    public static HttpApp getInstance(Context applicationContext, int port) throws IOException {
        if (http_app == null) {
            synchronized (HttpApp.class) {
                if (http_app == null) {
                    http_app = new HttpApp(applicationContext, port);
                }
            }
        }
        return http_app;
    }

}