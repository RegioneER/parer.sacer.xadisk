/*
 * Engineering Ingegneria Informatica S.p.A.
 *
 * Copyright (C) 2023 Regione Emilia-Romagna
 * <p/>
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package org.xadisk.filesystem.utilities;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.Date;

public class Logger {
    private File logFilePath;
    private PrintStream logFileStream;
    private final byte logLevel;
    private long logFileSize;
    public static final byte ERROR = 1;
    public static final byte WARNING = 2;
    public static final byte INFORMATIONAL = 3;
    public static final byte DEBUG = 4;
    private static final int LOG_FILE_MAX_SIZE = 1000000;
    private Object[] date;
    MessageFormat formatter = new MessageFormat("{0,date} {0,time}");
    
    public Logger(File logFilePath, byte logLevel) throws IOException {
        if(logFilePath.exists()) {
            logFilePath.renameTo(new File(logFilePath.getAbsolutePath() + "_" + System.currentTimeMillis()));
        }
        this.logFilePath = logFilePath;
        this.logFileStream = new PrintStream(new FileOutputStream(logFilePath), true);
        this.logLevel = logLevel;
        this.date = new Object[1];
        this.date[0] = new Date();
    }

    private void rotateLogFile() throws FileNotFoundException {
        logFileStream.close();
        logFilePath.renameTo(new File(this.logFilePath.getAbsolutePath() + "_" + System.currentTimeMillis()));
        logFileSize = 0;
        logFileStream = new PrintStream(new FileOutputStream(logFilePath), true);
    }

    public void logError(String msg) {
        if (logLevel >= ERROR) {
            writeMessageToLogFile(msg);
        }
    }

    public void logWarning(String msg) {
        if (logLevel >= WARNING) {
            writeMessageToLogFile(msg);
        }
    }

    public void logInfo(String msg) {
        if (logLevel >= INFORMATIONAL) {
            writeMessageToLogFile(msg);
        }
    }

    public void logDebug(String msg) {
        if (logLevel >= DEBUG) {
            writeMessageToLogFile(msg);
        }
    }

    public void logThrowable(Throwable t, byte atLogLevel) {
        if (logLevel >= atLogLevel) {
            writeMessageToLogFile(t.getMessage());
            t.printStackTrace(logFileStream);
            logFileStream.println();
            logFileStream.flush();
            setLogFileSize(logFilePath.length());
        }
    }

    public void release() throws IOException {
        logFileStream.close();
    }
    
    private void writeMessageToLogFile(String msg) {
        StringBuffer sb = new StringBuffer();
        ((Date)date[0]).setTime(System.currentTimeMillis());
        formatter.format(date, sb, null);

        String logMsg = sb + " : " + msg + "\n\n";
        logFileStream.print(logMsg);
        logFileStream.flush();
        setLogFileSize(logFileSize + logMsg.length());
        //so logFileSize becomes number of chars (not bytes),
        //which is ok if we are saving a conversion call to bytes to count them.
    }

    private void setLogFileSize(long newLogFileSize) {
        logFileSize = newLogFileSize;
        if(logFileSize >= LOG_FILE_MAX_SIZE) {
            try {
                rotateLogFile();
            } catch(FileNotFoundException fnfe) {
                //cant help, so print on console.
                fnfe.printStackTrace();
            }
        }
    }
}
