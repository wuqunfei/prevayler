// Prevayler, The Free-Software Prevalence Layer
// Copyright 2001-2006 by Klaus Wuestefeld
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.
//
// Prevayler is a trademark of Klaus Wuestefeld.
// See the LICENSE file for license details.

package org.prevayler.implementation.journal;

import org.prevayler.foundation.Chunk;
import org.prevayler.foundation.DurableInputStream;
import org.prevayler.foundation.DurableOutputStream;
import org.prevayler.foundation.Guided;
import org.prevayler.foundation.StopWatch;
import org.prevayler.foundation.monitor.Monitor;
import org.prevayler.implementation.PrevaylerDirectory;
import org.prevayler.implementation.TransactionGuide;
import org.prevayler.implementation.TransactionTimestamp;
import org.prevayler.implementation.publishing.TransactionSubscriber;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;

/**
 * A Journal that will write all transactions to .journal files.
 */
public class PersistentJournal<S> implements Journal<S> {

    private final PrevaylerDirectory _directory;

    private DurableOutputStream _outputJournal;

    private final long _journalSizeThresholdInBytes;

    private final long _journalAgeThresholdInMillis;

    private StopWatch _journalAgeTimer;

    private long _nextTransaction;

    private boolean _nextTransactionInitialized = false;

    private Monitor _monitor;

    private final String _journalSuffix;

    /**
     * @param directory
     * @param journalSizeThresholdInBytes
     *            Size of the current journal file beyond which it is closed and
     *            a new one started. Zero indicates no size threshold. This is
     *            useful journal backup purposes.
     * @param journalAgeThresholdInMillis
     *            Age of the current journal file beyond which it is closed and
     *            a new one started. Zero indicates no age threshold. This is
     *            useful journal backup purposes.
     */
    public PersistentJournal(PrevaylerDirectory directory, long journalSizeThresholdInBytes, long journalAgeThresholdInMillis, String journalSuffix, Monitor monitor) throws IOException {
        PrevaylerDirectory.checkValidJournalSuffix(journalSuffix);

        _monitor = monitor;
        _directory = directory;
        _directory.produceDirectory();
        _journalSizeThresholdInBytes = journalSizeThresholdInBytes;
        _journalAgeThresholdInMillis = journalAgeThresholdInMillis;
        _journalSuffix = journalSuffix;
    }

    public <R, E extends Exception> void append(TransactionGuide<S, R, E> guide) {
        if (!_nextTransactionInitialized)
            throw new IllegalStateException("Journal.update() has to be called at least once before Journal.append().");

        DurableOutputStream myOutputJournal;
        DurableOutputStream outputJournalToClose = null;

        guide.startTurn();
        try {
            guide.checkSystemVersion(_nextTransaction);

            if (!isOutputJournalStillValid()) {
                outputJournalToClose = _outputJournal;
                _outputJournal = createOutputJournal(_nextTransaction, guide);
                _journalAgeTimer = StopWatch.start();
            }

            _nextTransaction++;

            myOutputJournal = _outputJournal;
        } finally {
            guide.endTurn();
        }

        try {
            myOutputJournal.sync(guide);
        } catch (Exception exception) {
            abort(exception, "writing to", guide);
        }

        guide.startTurn();
        try {
            try {
                if (outputJournalToClose != null)
                    outputJournalToClose.close();
            } catch (Exception exception) {
                abort(exception, "closing", guide);
            }
        } finally {
            guide.endTurn();
        }
    }

    private boolean isOutputJournalStillValid() {
        return _outputJournal != null && !isOutputJournalTooBig() && !isOutputJournalTooOld();
    }

    private boolean isOutputJournalTooOld() {
        return _journalAgeThresholdInMillis != 0 && _journalAgeTimer.millisEllapsed() >= _journalAgeThresholdInMillis;
    }

    private boolean isOutputJournalTooBig() {
        return _journalSizeThresholdInBytes != 0 && _outputJournal.file().length() >= _journalSizeThresholdInBytes;
    }

    private DurableOutputStream createOutputJournal(long transactionNumber, Guided guide) {
        File file = _directory.journalFile(transactionNumber, _journalSuffix);
        try {
            return new DurableOutputStream(file);
        } catch (Exception exception) {
            abort(exception, "creating", guide);
            return null;
        }
    }

    /**
     * IMPORTANT: This method cannot be called while the log() method is being
     * called in another thread. If there are no journal files in the directory
     * (when a snapshot is taken and all journal files are manually deleted, for
     * example), the initialTransaction parameter in the first call to this
     * method will define what the next transaction number will be. We have to
     * find clearer/simpler semantics.
     */
    public void update(TransactionSubscriber<S> subscriber, long initialTransactionWanted) {
        try {
            File initialJournal = _directory.findInitialJournalFile(initialTransactionWanted);

            if (initialJournal == null) {
                initializeNextTransaction(initialTransactionWanted, 1);
                return;
            }

            long nextTransaction = recoverPendingTransactions(subscriber, initialTransactionWanted, initialJournal);

            initializeNextTransaction(initialTransactionWanted, nextTransaction);
        } catch (Exception e) {
            throw new JournalError(e);
        }
    }

    private void initializeNextTransaction(long initialTransactionWanted, long nextTransaction) {
        if (_nextTransactionInitialized) {
            if (_nextTransaction < initialTransactionWanted)
                throw new JournalError("The transaction log has not yet reached transaction " + initialTransactionWanted + ". The last logged transaction was " + (_nextTransaction - 1) + ".");
            if (nextTransaction < _nextTransaction)
                throw new JournalError("Unable to find journal file containing transaction " + nextTransaction + ". Might have been manually deleted.");
            if (nextTransaction > _nextTransaction)
                throw new IllegalStateException();
            return;
        }
        _nextTransactionInitialized = true;
        _nextTransaction = initialTransactionWanted > nextTransaction ? initialTransactionWanted : nextTransaction;
    }

    private long recoverPendingTransactions(TransactionSubscriber<S> subscriber, long initialTransaction, File initialJournal) throws Exception {
        long recoveringTransaction = PrevaylerDirectory.journalVersion(initialJournal);
        File journal = initialJournal;
        DurableInputStream input = new DurableInputStream(journal, _monitor);

        while (true) {
            try {
                Chunk chunk = input.readChunk();

                if (recoveringTransaction >= initialTransaction) {
                    if (!journal.getName().endsWith(_journalSuffix)) {
                        throw new JournalError("There are transactions needing to be recovered from " + journal + ", but only " + _journalSuffix + " files are supported");
                    }

                    TransactionTimestamp<S, ?, ?> entry = TransactionTimestamp.fromChunk(chunk);

                    if (entry.systemVersion() != recoveringTransaction) {
                        throw new JournalError("Expected " + recoveringTransaction + " but was " + entry.systemVersion());
                    }

                    subscriber.receive(entry);
                }

                recoveringTransaction++;

            } catch (EOFException eof) {
                File nextFile = _directory.journalFile(recoveringTransaction, _journalSuffix);
                if (journal.equals(nextFile)) {
                    // The first transaction in this log file is incomplete. We
                    // need to reuse this file name.
                    PrevaylerDirectory.renameUnusedFile(journal);
                }
                journal = nextFile;
                if (!journal.exists())
                    break;
                input = new DurableInputStream(journal, _monitor);
            }
        }
        return recoveringTransaction;
    }

    private void abort(Exception exception, String action, Guided guide) {
        guide.abortTurn("All transaction processing is now aborted. An exception was thrown while " + action + " a journal file.", exception);
    }

    public void close() {
        if (_outputJournal != null) {
            try {
                _outputJournal.close();
            } catch (Exception e) {
                throw new JournalError(e);
            }
        }
    }

    public long nextTransaction() {
        if (!_nextTransactionInitialized) {
            throw new IllegalStateException("update() must be called at least once");
        }
        return _nextTransaction;
    }

}
