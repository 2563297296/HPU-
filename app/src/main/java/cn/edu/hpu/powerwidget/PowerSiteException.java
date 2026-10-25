package cn.edu.hpu.powerwidget;

final class PowerSiteException extends Exception {
    PowerSiteException(String message) {
        super(message);
    }

    PowerSiteException(String message, Throwable cause) {
        super(message, cause);
    }
}
