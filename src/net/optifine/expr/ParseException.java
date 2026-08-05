package net.optifine.expr;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class ParseException extends Exception
{
    public ParseException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public ParseException(String message)
    {
        super(message);
    }
}
