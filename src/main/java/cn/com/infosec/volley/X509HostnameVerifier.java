package cn.com.infosec.volley;

import java.io.IOException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/**
 * Interface for checking if a hostname matches the names stored inside the
 * server's X.509 certificate.  Implements javax.net.ssl.HostnameVerifier, but
 * we don't actually use that interface.  Instead we added some methods that
 * take String parameters (instead of javax.net.ssl.HostnameVerifier's
 * SSLSession).  JUnit is a lot easier this way!  :-)
 * <p/>
 * We provide the HostnameVerifier.DEFAULT, HostnameVerifier.STRICT, and
 * HostnameVerifier.ALLOW_ALL implementations.  But feel free to define
 * your own implementation!
 * <p/>
 * Inspired by Sebastian Hauer's original StrictSSLProtocolSocketFactory in the
 * HttpClient "contrib" repository.
 *
 * @author Julius Davies
 * @author <a href="mailto:hauer@psicode.com">Sebastian Hauer</a>
 *
 * @since 4.0 (8-Dec-2006)
 */
public interface X509HostnameVerifier extends HostnameVerifier {

    boolean verify(String host, SSLSession session);

    void verify(String host, SSLSocket ssl) throws IOException;

    void verify(String host, X509Certificate cert) throws SSLException;

    /**
     * Checks to see if the supplied hostname matches any of the supplied CNs
     * or "DNS" Subject-Alts.  Most implementations only look at the first CN,
     * and ignore any additional CNs.  Most implementations do look at all of
     * the "DNS" Subject-Alts. The CNs or Subject-Alts may contain wildcards
     * according to RFC 2818.
     *
     * @param cns         CN fields, in order, as extracted from the X.509
     *                    certificate.
     * @param subjectAlts Subject-Alt fields of type 2 ("DNS"), as extracted
     *                    from the X.509 certificate.
     * @param host        The hostname to verify.
     * @throws SSLException If verification failed.
     */
    void verify(String host, String[] cns, String[] subjectAlts)
          throws SSLException;


}
