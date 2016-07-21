package com.karumi.dexter.asv;

import android.util.AndroidRuntimeException;

/**
 * Created by nubor on 20/07/2016.
 */
final class RemoteServiceException extends AndroidRuntimeException {
    public RemoteServiceException(String msg) {
        super(msg);
    }
}
