package cn.com.infosec.volley;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An entity that can be sent or received with an HTTP message.
 * Entities can be found in some in
 * {@link HttpResponse responses}, where they are optional.
 * <p>
 * There are three distinct types of entities in HttpCore,
 * depending on where their {@link #getContent content} originates:
 * <ul>
 * <li><b>streamed</b>: The content is received from a stream, or
 *     generated on the fly. In particular, this category includes
 *     entities being received from a connection.
 *     {@link #isStreaming Streamed} entities are generally not
 *      {@link #isRepeatable repeatable}.
 *     </li>
 * <li><b>self-contained</b>: The content is in memory or obtained by
 *     means that are independent from a connection or other entity.
 *     Self-contained entities are generally {@link #isRepeatable repeatable}.
 *     </li>
 * <li><b>wrapping</b>: The content is obtained from another entity.
 *     </li>
 * </ul>
 * This distinction is important for connection management with incoming
 * entities. For entities that are created by an application and only sent
 * using the HTTP components framework, the difference between streamed
 * and self-contained is of little importance. In that case, it is suggested
 * to consider non-repeatable entities as streamed, and those that are
 * repeatable (without a huge effort) as self-contained.
 *
 * @since 4.0
 */
public class HttpEntity {

    private InputStream content;
    private long length;

    /**
     * Buffer size for output stream processing.
     *
     * @since 4.3
     */
    private static final int OUTPUT_BUFFER_SIZE = 4096;

    private String contentType;
    private String contentEncoding;
    private boolean chunked;

    /**
     * Creates a new basic entity.
     * The content is initially missing, the content length
     * is set to a negative number.
     */
    public HttpEntity() {
        super();
        length = -1;
    }

    /**
     * Tells the length of the content, if known.
     *
     * @return  the number of bytes of the content, or
     *          a negative number if unknown. If the content length is known
     *          but exceeds {@link Long#MAX_VALUE Long.MAX_VALUE},
     *          a negative number is returned.
     */
    public long getContentLength() {
        return this.length;
    }

    /**
     * Obtains the Content-Type header, if known.
     * This is the header that should be used when sending the entity,
     * or the one that was received with the entity. It can include a
     * charset attribute.
     *
     * @return  the Content-Type header for this entity, or
     *          {@code null} if the content type is unknown
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Obtains the Content-Encoding header, if known.
     * This is the header that should be used when sending the entity,
     * or the one that was received with the entity.
     * Wrapping entities that modify the content encoding should
     * adjust this header accordingly.
     *
     * @return  the Content-Encoding header for this entity, or
     *          {@code null} if the content encoding is unknown
     */
    public String getContentEncoding() {
        return contentEncoding;
    }

    /**
     * Returns a content stream of the entity.
     * {@link #isRepeatable Repeatable} entities are expected
     * to create a new instance of {@link InputStream} for each invocation
     * of this method and therefore can be consumed multiple times.
     * Entities that are not {@link #isRepeatable repeatable} are expected
     * to return the same {@link InputStream} instance and therefore
     * may not be consumed more than once.
     * <p>
     * IMPORTANT: Please note all entity implementations must ensure that
     * all allocated resources are properly deallocated after
     * the {@link InputStream#close()} method is invoked.
     *
     * @return content stream of the entity.
     *
     * @throws IOException if the stream could not be created
     * @throws UnsupportedOperationException
     *  if entity content cannot be represented as {@link InputStream}.
     *
     * @see #isRepeatable()
     */
    public InputStream getContent() throws IllegalStateException {
        if(this.content == null) {
            throw new RuntimeException("Content has not been provided");
        }
        return this.content;
    }

    /**
     * Tells if the entity is capable of producing its data more than once.
     * A repeatable entity's getContent() and writeTo(OutputStream) methods
     * can be called more than once whereas a non-repeatable entity's can not.
     * @return true if the entity is repeatable, false otherwise.
     */
    public boolean isRepeatable() {
        return false;
    }

    /**
     * Specifies the length of the content.
     *
     * @param len       the number of bytes in the content, or
     *                  a negative number to indicate an unknown length
     */
    public void setContentLength(final long len) {
        this.length = len;
    }

    public void setContent (InputStream content) {
        this.content = content;
    }

    /**
     * Specifies the Content-Type header.
     * The default implementation sets the value of the
     * {@link #contentType contentType} attribute.
     *
     * @param contentType       the new Content-Type header, or
     *                          {@code null} to unset
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Writes the entity content out to the output stream.
     * <p>
     * IMPORTANT: Please note all entity implementations must ensure that
     * all allocated resources are properly deallocated when this method
     * returns.
     *
     * @param outstream the output stream to write entity content to
     *
     * @throws IOException if an I/O error occurs
     */
    public void writeTo(final OutputStream outstream) throws IOException {
        if (outstream == null) {
            throw new RuntimeException("Output stream is null");
        }
        final InputStream instream = getContent();
        try {
            int l;
            final byte[] tmp = new byte[OUTPUT_BUFFER_SIZE];
            while ((l = instream.read(tmp)) != -1) {
                outstream.write(tmp, 0, l);
            }
        } finally {
            instream.close();
        }
    }

    /**
     * Tells whether this entity depends on an underlying stream.
     * Streamed entities that read data directly from the socket should
     * return {@code true}. Self-contained entities should return
     * {@code false}. Wrapping entities should delegate this call
     * to the wrapped entity.
     *
     * @return  {@code true} if the entity content is streamed,
     *          {@code false} otherwise
     */
    public boolean isStreaming() {
        try {
            return this.content != null && this.content.available() != 0;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Tells about chunked encoding for this entity.
     * The primary purpose of this method is to indicate whether
     * chunked encoding should be used when the entity is sent.
     * For entities that are received, it can also indicate whether
     * the entity was received with chunked encoding.
     * <p>
     * The behavior of wrapping entities is implementation dependent,
     * but should respect the primary purpose.
     * </p>
     *
     * @return  {@code true} if chunked encoding is preferred for this
     *          entity, or {@code false} if it is not
     */
    public boolean isChunked() {
        return this.chunked;
    }


    /**
     * Specifies the Content-Encoding header.
     * The default implementation sets the value of the
     * {@link #contentEncoding contentEncoding} attribute.
     *
     * @param contentEncoding   the new Content-Encoding header, or
     *                          {@code null} to unset
     */
    public void setContentEncoding(String contentEncoding) {
        this.contentEncoding = contentEncoding;
    }


    /**
     * Specifies the 'chunked' flag.
     * <p>
     * Note that the chunked setting is a hint only.
     * If using HTTP/1.0, chunking is never performed.
     * Otherwise, even if chunked is false, HttpClient must
     * use chunk coding if the entity content length is
     * unknown (-1).
     * <p>
     * The default implementation sets the value of the
     * {@link #chunked chunked} attribute.
     *
     * @param b         the new 'chunked' flag
     */
    public void setChunked(final boolean b) {
        this.chunked = b;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append('[');
        if (contentType != null) {
            sb.append("Content-Type: ");
            sb.append(contentType);
            sb.append(',');
        }
        if (contentEncoding != null) {
            sb.append("Content-Encoding: ");
            sb.append(contentEncoding);
            sb.append(',');
        }
        final long len = getContentLength();
        if (len >= 0) {
            sb.append("Content-Length: ");
            sb.append(len);
            sb.append(',');
        }
        sb.append("Chunked: ");
        sb.append(chunked);
        sb.append(']');
        return sb.toString();
    }

}
