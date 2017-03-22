package cn.com.infosec.volley;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * User: mcxiaoke
 * Date: 15/3/17
 * Time: 14:47
 */
public class InternalUtils {

    // http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
    private final static char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    private static String convertToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_CHARS[v >>> 4];
            hexChars[j * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String sha1Hash(String text) {
        String hash = null;
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-1");
            final byte[] bytes = text.getBytes("UTF-8");
            digest.update(bytes, 0, bytes.length);
            hash = convertToHex(digest.digest());
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return hash;
    }

    public static SSLSocketFactory buildSSLSocketFactory(InputStream trustStream, InputStream keyStream, String keyPassword, int httpsPort) {
        try {
            // Create SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");
            if (trustStream == null || trustStream.available() <= 0) {
                return null;
            }
            //存储服务器根证书
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            KeyStore trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustKeyStore.load(null);
            Certificate cert = certificateFactory.generateCertificate(trustStream);
            trustKeyStore.setCertificateEntry("trustCert", cert);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("X509");
            trustManagerFactory.init(trustKeyStore);
            if (keyStream != null && keyStream.available() > 0) {
                //初始化两个KeyStore用于和客户端证书
                KeyStore clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                clientKeyStore.load(keyStream, keyPassword.toCharArray());
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("X509");
                keyManagerFactory.init(clientKeyStore,keyPassword.toCharArray());
                sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
            } else {
                sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            }
            return sslContext.getSocketFactory();
        } catch ( CertificateException | NoSuchAlgorithmException | IOException | KeyManagementException e) {
            e.printStackTrace();
            return null;
        } catch (KeyStoreException e) {
            e.printStackTrace();
            return null;
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
            return null;
        }
    }

/*    private static HttpClient convertSSLClient(HttpClient httpClient, InputStream trustStream, InputStream keyStream, String keyPassword, int httpsPort) {
        try {
            //初始化两个KeyStore用于和客户端证书
            KeyStore clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            clientKeyStore.load(keyStream, keyPassword.toCharArray());
            //存储服务器根证书
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            KeyStore trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustKeyStore.load(null);
            Certificate cert = certificateFactory.generateCertificate(trustStream);
            trustKeyStore.setCertificateEntry("trustCert", cert);
            //取得KeyManagerFactory和TrustManagerFactory的X509密钥管理器实例
            KeyManagerFactory clientKeyManager = KeyManagerFactory.getInstance("X509");
            TrustManagerFactory trustKeyManager = TrustManagerFactory.getInstance("X509");
            //使用KeyStore初始化秘钥管理器
            clientKeyManager.init(clientKeyStore,keyPassword.toCharArray());
            trustKeyManager.init(trustKeyStore);
            //获取一个SSLSocketFactory
            org.apache.http.conn.ssl.SSLSocketFactory sslSocketFactory = new org.apache.http.conn.ssl.SSLSocketFactory(clientKeyStore,keyPassword,trustKeyStore);
            //初始化一个
            Scheme scheme = new Scheme("https",sslSocketFactory,httpsPort==0?443:httpsPort);
            //为httpClient注册Schema
            httpClient.getConnectionManager().getSchemeRegistry().register(scheme);
            return httpClient;
        } catch (KeyStoreException | CertificateException | UnrecoverableKeyException | NoSuchAlgorithmException | KeyManagementException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }*/

    /**
     *
     * The summery of the class.
     * <ul>
     * <li>File name : HttpsClient.java</li>
     * <li>Description
     * <li>Copyright : Copyright(C)2008-2014</li>
     * <li>remark :</li>
     * <li>create date : 2014-1-8</li>
     * </ul>
     *
     * @version 1.0
     * @author	 */
    class DefaultTrustManager implements X509TrustManager {

        InputStream trustcertInput;

        public DefaultTrustManager(InputStream trustcertInput1) {
            trustcertInput=trustcertInput1;
        }



        @Override
        public void checkClientTrusted(X509Certificate[] arg0, String arg1)
                throws CertificateException {
            System.out.println("=======================11=="+arg0.length);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] arg0, String arg1)
                throws CertificateException {
            System.out.println("=======================22=="+arg0.length);
            System.out.println("=======================22=="+arg0[0].getSubjectDN().toString());
            try{

                arg0[0].checkValidity();

                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                X509Certificate oCert = (X509Certificate)cf.generateCertificate(trustcertInput);
                PublicKey pubkey=oCert.getPublicKey();
                arg0[0].verify(pubkey);
                trustcertInput.close();
                System.out.println("=======================33==");
            }catch(Exception e){
                throw new CertificateException(e.getMessage());
            }
        }



        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
}
