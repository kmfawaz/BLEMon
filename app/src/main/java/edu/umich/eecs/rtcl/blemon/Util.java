package edu.umich.eecs.rtcl.blemon;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;


import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;



import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;

public class Util {
    public static final String URL_UPLOAD = ""; //need to replace with your own server.


    public static boolean isWiFiConnected (Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return mWifi.isConnected();
        //return true;
    }

    public static BufferedWriter initialzeWriter (String folder, String fileName, Context context) throws IOException {
        //System.out.println(record);
        File mydir = context.getDir(folder, Context.MODE_PRIVATE); //Creating an internal directory
        File file = new File(mydir, fileName);

        FileOutputStream f = new FileOutputStream(file,true);
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(f));
        writer.write("this is the header line for file:\t" + fileName + "\n");
        return writer;
    }

    public static class FileUploader extends AsyncTask<File, Void, Void> {

        private void postFile (File file) {
            //delete if successful
            //perform the actual upload and if successful:
            //upload only using wifi
            //hash of IMEI is the unique key per user

            //gzip file
            //upload file gzipped


            if (ScannerService.DEV_ID.equals("")) {
                //process log did not boot yet; so the uid string is empty
                return;
            }
            try {
                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                trustStore.load(null, null);

                SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
                sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

                HttpParams params = new BasicHttpParams();
                HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
                HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

                SchemeRegistry registry = new SchemeRegistry();
                registry.register(new Scheme("https", sf, 951));

                ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

                HttpClient httpclient = new DefaultHttpClient(ccm,params);
                HttpPost httppost = new HttpPost(URL_UPLOAD);

                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
//			    builder.addBinaryBody("file", file,ContentType.MULTIPART_FORM_DATA,file.getName());
                builder.addPart("file", new FileBody(file,ContentType.MULTIPART_FORM_DATA,file.getName()));
                builder.addTextBody("path", ScannerService.DEV_ID);
                httppost.setEntity(builder.build());

                //System.out.println(file.length());

                HttpResponse response = httpclient.execute(httppost);
                int code = response.getStatusLine().getStatusCode();
                BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(response.getEntity().getContent()));
                String s = "";
                String totalResponse = "";
                while ((s=bufferedReader.readLine())!=null) {
                    totalResponse+=s;
                }

                if (code ==200 && totalResponse.equals("success")) {
                    //uploaded successfully;
                    Log.v("MYBLE","uploaded successfully, file: \t"+file);
                    file.delete();
                    //uncompressedFile.delete();
                }

            } catch (Exception e) {
                e.printStackTrace(); //do nothing
            }
        }
        @Override
        protected Void doInBackground(File... files) {

            setStartFlag(files);
            if (files[0].getAbsolutePath().contains("ble_log")) {
                Log.v("MYBLE","START");
            }
            try {
                for (File singleFile:files) {
                    //System.out.println(singleFile.getName());
                    if (files[0].getAbsolutePath().contains("app_system_log")) {
                        Log.v("MYBLE",singleFile.getName()+"........."+ ScannerService.systemFlag);
                    }

                    String dstFileStr = singleFile.getAbsolutePath();
                    if (!singleFile.getName().contains(".gz")){
                        dstFileStr = singleFile.getAbsolutePath()+".gz";
                        try {
                            gzipFile(singleFile.getAbsolutePath(), dstFileStr);

                            singleFile.delete();//delete the uncompressed file
                            Log.v("MYBLE", "after gzipping deleting:" + singleFile.getAbsolutePath());
                        } catch (Exception e) {
                            Log.v("MYBLE", "gzipping error");
                            e.printStackTrace();
                            setEndFlag(files);
                            return null;
                            //upload failed because compression failed
                        }
                    }

                    File dstFile = new File(dstFileStr);
                    postFile(dstFile);
                }
            }
            catch (Exception e) { }

            setEndFlag(files);
            if (files[0].getAbsolutePath().contains("ble_log")) {
                Log.v("MYBLE","END");
            }
            return null;
        }

    }

    static void setStartFlag (File [] files) {
        setFlag (files, true);
    }

    static void setEndFlag (File [] files) {
        setFlag (files, false);
    }


    static private void setFlag (File [] files, boolean flagValue) {
        if (files.length<1) {
            return;
        }

        if (files[0].getAbsolutePath().contains("ble_log")) {
            ScannerService.systemFlag = flagValue;
        }
    }

    static class MySSLSocketFactory extends SSLSocketFactory {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        public MySSLSocketFactory(KeyStore truststore)
                throws NoSuchAlgorithmException, KeyManagementException,
                KeyStoreException, UnrecoverableKeyException {
            super(truststore);

            TrustManager tm = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain,
                                               String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain,
                                               String authType) throws CertificateException {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };

            sslContext.init(null, new TrustManager[] { tm }, null);
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port,
                                   boolean autoClose) throws IOException, UnknownHostException {
            return sslContext.getSocketFactory().createSocket(socket, host,
                    port, autoClose);
        }

        @Override
        public Socket createSocket() throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }
    }

    public static void gzipFile(String source_filepath,
                                String destinaton_zip_filepath) throws IOException {

        //extra caution:
        File file = new File(source_filepath);
        if(!file.exists()) {
            return;
        }

        byte[] buffer = new byte[1024];
        try {

            FileOutputStream fileOutputStream = new FileOutputStream(
                    destinaton_zip_filepath);

            GZIPOutputStream gzipOuputStream = new GZIPOutputStream(
                    fileOutputStream);

            FileInputStream fileInput = new FileInputStream(source_filepath);

            int bytes_read;

            while ((bytes_read = fileInput.read(buffer)) > 0) {
                gzipOuputStream.write(buffer, 0, bytes_read);
            }

            fileInput.close();

            gzipOuputStream.finish();
            gzipOuputStream.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }




}


