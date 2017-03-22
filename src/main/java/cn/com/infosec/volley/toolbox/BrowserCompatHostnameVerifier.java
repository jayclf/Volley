package cn.com.infosec.volley.toolbox;

import javax.net.ssl.SSLException;

import cn.com.infosec.volley.AbstractVerifier;

/**
 * The HostnameVerifier that works the same way as Curl and Firefox.
 * <p/>
 * The hostname must match either the first CN, or any of the subject-alts.
 * A wildcard can occur in the CN, and in any of the subject-alts.
 * <p/>
 * The only difference between BROWSER_COMPATIBLE and STRICT is that a wildcard 
 * (such as "*.foo.com") with BROWSER_COMPATIBLE matches all subdomains, 
 * including "a.b.foo.com".
 * 
 * @author Julius Davies
 */
public class BrowserCompatHostnameVerifier extends AbstractVerifier {

    public final void verify(
            final String host, 
            final String[] cns,
            final String[] subjectAlts) throws SSLException {
        verify(host, cns, subjectAlts, false);
    }

    @Override
    public final String toString() { 
        return "BROWSER_COMPATIBLE"; 
    }
    
}
