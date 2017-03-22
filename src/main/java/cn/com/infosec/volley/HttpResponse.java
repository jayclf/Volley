package cn.com.infosec.volley;

import cn.com.infosec.volley.toolbox.HttpStatusLine;

import java.util.HashMap;
import java.util.Map;

/**
 * After receiving and interpreting a request message, a server responds
 * with an HTTP response message.
 * <pre>
 *     Response      = Status-Line
 *                     *(( general-header
 *                      | response-header
 *                      | entity-header ) CRLF)
 *                     CRLF
 *                     [ message-body ]
 * </pre>
 *
 * @since 4.0
 */
public class HttpResponse {

    private Map<String,String> mHeaders;

    private HttpEntity mEntity;

    private HttpStatusLine mStatusLine;

    /**
     * Creates a response from a status line.
     * The response will not have a reason phrase catalog and
     * use the system default locale.
     *
     * @param statusline        the status line
     */
    public HttpResponse(final HttpStatusLine statusline) {
        super();
        mStatusLine = statusline;
    }

    /**
     * Obtains the status line of this response.
     * The status line can be set using one of the
     * {@link #setStatusLine setStatusLine} methods,
     * or it can be initialized in a constructor.
     *
     * @return  the status line, or {@code null} if not yet set
     */
    public HttpStatusLine getStatusLine(){
        return mStatusLine;
    }


    /**
     * Sets the status line of this response.
     *
     * @param statusline the status line of this response
     */
    public void setStatusLine(HttpStatusLine statusline){
        mStatusLine = statusline;
    }

    /**
     * Sets the status line of this response with a reason phrase.
     *
     * @param ver       the HTTP version
     * @param code      the status code
     * @param reason    the reason phrase, or {@code null} to omit
     */
    public void setStatusLine(ProtocolVersion ver, int code, String reason) {
        mStatusLine = new HttpStatusLine(ver,code,reason);
    }


    /**
     * Obtains the message entity of this response, if any.
     * The entity is provided by calling {@link #setEntity setEntity}.
     *
     * @return  the response entity, or
     *          {@code null} if there is none
     */
    public HttpEntity getEntity() {
        return mEntity;
    }

    /**
     * Associates a response entity with this response.
     * <p>
     * Please note that if an entity has already been set for this response and it depends on
     * an input stream ({@link HttpEntity#isStreaming()} returns {@code true}),
     * it must be fully consumed in order to ensure release of resources.
     *
     * @param entity    the entity to associate with this response, or
     *                  {@code null} to unset
     *
     * @see HttpEntity#isStreaming()
     */
    public void setEntity(HttpEntity entity) {
        mEntity = entity;
    }

    public Map<String,String> getHeaders() {
        return mHeaders;
    }

    public void setHeader(Map<String,String> header) {
        mHeaders = header;
    }

    public void addHeader(String key,String value) {
        if (null == mHeaders) {
            mHeaders = new HashMap<>();
        }
        mHeaders.put(key, value);
    }

    public void removeHeader(String key) {
        mHeaders.remove(key);
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getStatusLine());
        sb.append(' ');
        if (mEntity != null) {
            sb.append(' ');
            sb.append(mEntity);
        }
        return sb.toString();
    }

}
