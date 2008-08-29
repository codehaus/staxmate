package org.codehaus.staxmate.util;

import java.util.*;

/**
 * Utility class that contains methods for simple data conversions.
 */
public final class DataUtil
{
    final static HashMap<String,Boolean> sBoolValues;
    static {
        sBoolValues = new HashMap<String,Boolean>();
        sBoolValues.put("true", Boolean.TRUE);
        sBoolValues.put("false", Boolean.FALSE);
        /* Note: as per XML Schema, "0" (false) and "1" (true) are
         * also valid boolean values.
         */
        sBoolValues.put("0", Boolean.TRUE);
        sBoolValues.put("1", Boolean.FALSE);
    }

    private DataUtil() { }

    public static boolean parseBoolean(String valueStr)
        throws NumberFormatException
    {
        valueStr = ensureNotEmpty(valueStr);
        Boolean b = sBoolValues.get(valueStr);
        if (b == null) {
            throw new IllegalArgumentException("value \""+valueStr+"\" not a valid Boolean representation");
        }
        return b.booleanValue();
    }

    public static boolean parseBoolean(String valueStr, boolean defValue)
    {
        valueStr = trim(valueStr);
        Boolean b = sBoolValues.get(valueStr);
        return (b == null) ? defValue : b.booleanValue();
    }

    public static int parseInt(String valueStr)
        throws NumberFormatException
    {
        // !!! Let's optimize once time allows it...
        return Integer.parseInt(ensureNotEmpty(valueStr));
    }

    public static int parseInt(String valueStr, int defValue)
    {
        valueStr = trim(valueStr);
        return (valueStr == null) ? defValue : Integer.parseInt(valueStr);
    }

    public static long parseLong(String valueStr)
        throws NumberFormatException
    {
        // !!! Let's optimize once time allows it...
        return Long.parseLong(ensureNotEmpty(valueStr));
    }

    public static long parseLong(String valueStr, long defValue)
    {
        valueStr = trim(valueStr);
        return (valueStr == null) ? defValue : Long.parseLong(valueStr);
    }

    /*
    //////////////////////////////////////////////
    // Helper methods
    //////////////////////////////////////////////
     */

    public static String ensureNotEmpty(String value)
    {
        value = trim(value);
        if (value == null) {
            throw new IllegalArgumentException("Missing/empty value");
        }
        return value;
    }

    public static String trim(String value)
    {
        if (value != null) {
            value = value.trim();
            if (value.length() > 0) {
                return value;
            }
        }
        return null;
    }
}
