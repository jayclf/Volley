package cn.com.infosec.volley.toolbox;

import cn.com.infosec.volley.AbstractVerifier;

/**
 * The ALLOW_ALL HostnameVerifier essentially turns hostname verification
 * off. This implementation is a no-op, and never throws the SSLException.
 * 
 * @author Julius Davies
 */
public class AllowAllHostnameVerifier extends AbstractVerifier {

    public final void verify(
            final String host, 
            final String[] cns,
            final String[] subjectAlts) {
        // Allow everything - so never blowup.
    }

    @Override
    public final String toString() { 
        return "ALLOW_ALL"; 
    }
    
}
