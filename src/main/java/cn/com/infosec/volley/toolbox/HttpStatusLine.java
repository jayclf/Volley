package cn.com.infosec.volley.toolbox;

import cn.com.infosec.volley.ProtocolVersion;

import java.io.Serializable;


/**
 * Http状态行
 */
public class HttpStatusLine implements Cloneable, Serializable {

    private static final long serialVersionUID = -2443303766890459269L;
    /** 协议版本. */
    private final ProtocolVersion protoVersion;
    /** 状态码. */
    private final int statusCode;
    /** 状态短语. */
    private final String reasonPhrase;

    /**
     * 使用给定的协议版本，状态码和状态短语创建一个状态行.
     * @param version           响应的Http版本，通常是这样:"HTTP/1.1"
     * @param statusCode        响应的状态码
     * @param reasonPhrase      和状态码相对应的状态短语,有可能为{@code null}
     */
    public HttpStatusLine(final ProtocolVersion version, final int statusCode, final String reasonPhrase) {
        super();
        this.protoVersion = version;
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
    }


    public int getStatusCode() {
        return statusCode;
    }

    public ProtocolVersion getProtocolVersion() {
        return protoVersion;
    }

    public String getReasonPhrase() {
        return this.reasonPhrase;
    }

    @Override
    public String toString() {
        // 创建容器
        StringBuilder builder = new StringBuilder();
        // 添加协议
        builder.append(protoVersion.getProtocol());
        builder.append('/');
        builder.append(Integer.toString(protoVersion.getMajor()));
        builder.append('.');
        builder.append(Integer.toString(protoVersion.getMinor()));
        // 添加状态码
        builder.append(' ');
        builder.append(Integer.toString(statusCode));
        builder.append(' ');
        // 添加响应信息
        if (reasonPhrase != null) {
            builder.append(reasonPhrase);
        }
        // 返回数据,最终格式为：HTTP/1.1 200 OK
        return builder.toString();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

}
