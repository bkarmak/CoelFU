package com.example.biswajit.androidusb;

public class ByteArrayFinder {
    private ByteArrayFinder() {}
    public static int find(byte[] source, byte[] match) {
        // sanity checks
        if(source == null || match == null)
            return -1;
        if(source.length == 0 || match.length == 0)
            return -1;
        int ret = -1;
        int spos = 0;
        int mpos = 0;
        byte m = match[mpos];
        for( ; spos < source.length; spos++ ) {
            if(m == source[spos]) {
                // starting match
                if(mpos == 0)
                    ret = spos;
                    // finishing match
                else if(mpos == match.length - 1)
                    return ret;
                mpos++;
                m = match[mpos];
            }
            else {
                ret = -1;
                mpos = 0;
                m = match[mpos];
            }
        }
        return ret;
    }
}
