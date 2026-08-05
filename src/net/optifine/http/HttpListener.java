package net.optifine.http;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public interface HttpListener
{
    void finished(HttpRequest var1, HttpResponse var2);

    void failed(HttpRequest var1, Exception var2);
}
