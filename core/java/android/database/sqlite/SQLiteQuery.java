/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.database.sqlite;

import android.database.CursorWindow;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.util.Log;
import android.util.MutableBoolean;
import android.util.MutableInt;

import java.lang.ref.WeakReference;

/**
 * Represents a query that reads the resulting rows into a {@link SQLiteQuery}.
 * This class is used by {@link SQLiteCursor} and isn't useful itself.
 * <p>
 * This class is not thread-safe.
 * </p>
 */
public final class SQLiteQuery extends SQLiteProgram {
    private static final String TAG = "SQLiteQuery";

    private final CancellationSignal mCancellationSignal;
    private final MutableInt mNumRowsFound = new MutableInt(0);
    private final WeakReference<SQLiteQuery> mWeak = new WeakReference(this);
    private WeakReference<SQLiteConnection.PreparedStatement> mLastStmt = null;

    SQLiteQuery(SQLiteDatabase db, String query, CancellationSignal cancellationSignal) {
        super(db, query, null, cancellationSignal);

        mCancellationSignal = cancellationSignal;
    }

    /**
     * Reads rows into a buffer.
     *
     * @param window The window to fill into
     * @param startPos The start position for filling the window.
     * @param requiredPos The position of a row that MUST be in the window.
     * If it won't fit, then the query should discard part of what it filled.
     * @param countAllRows True to count all rows that the query would
     * return regardless of whether they fit in the window.
     * @param exhausted will be set to true if the full result set was consumed - never set to false
     * @return Number of rows that have been consumed from this result set so far. Might not be all
     * rows unless countAllRows is true.
     *
     * @throws SQLiteException if an error occurs.
     * @throws OperationCanceledException if the operation was canceled.
     */
    int traverse(CursorWindow window, int startPos, int requiredPos,
                 boolean countAllRows, MutableBoolean exhausted) {
        acquireReference();
        try {
            if (window != null) window.acquireReference();
            try {
                WeakReference<SQLiteConnection.PreparedStatement> stmt;
                stmt = getSession().executeForCursorWindow(getSql(), getBindArgs(),
                        window, startPos, requiredPos, countAllRows, getConnectionFlags(),
                        mCancellationSignal, exhausted, mNumRowsFound, this.mWeak);
                setLastStmt(stmt);
                return mNumRowsFound.value;
            } catch (SQLiteDatabaseCorruptException ex) {
                onCorruption();
                throw ex;
            } catch (SQLiteException ex) {
                Log.e(TAG, "exception: " + ex.getMessage() + "; query: " + getSql());
                throw ex;
            } finally {
                if (window != null) window.releaseReference();
            }
        } finally {
            releaseReference();
        }
    }

    private final void setLastStmt(WeakReference<SQLiteConnection.PreparedStatement> stmt) {
        if (mLastStmt == stmt) {
            return;
        }
        if (mLastStmt != null) {
            getSession().releaseStmtRef(mLastStmt, this.mWeak);
        }
        mLastStmt = stmt;
    }

    void onRequery() {
        setLastStmt(null);
    }

    void deactivate() {
        setLastStmt(null);
    }

    @Override
    public void close() {
        setLastStmt(null);
        super.close();
    }

    @Override
    public String toString() {
        return "SQLiteQuery: " + getSql();
    }
}
