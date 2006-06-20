// Prevayler, The Free-Software Prevalence Layer
// Copyright 2001-2006 by Klaus Wuestefeld
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.
//
// Prevayler is a trademark of Klaus Wuestefeld.
// See the LICENSE file for license details.

package org.prevayler.demos.scalability.prevayler;

import org.prevayler.PrevaylerFactory;
import org.prevayler.demos.scalability.TransactionConnection;
import org.prevayler.foundation.serialization.Serializer;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

public class PrevaylerTransactionSubject extends PrevaylerScalabilitySubject<TransactionSystem, TransactionConnection> {

    private final String _journalDirectory;

    private final String _journalSerializer;

    public PrevaylerTransactionSubject(String journalDirectory, String journalSerializer) throws java.io.IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        _journalDirectory = journalDirectory;
        _journalSerializer = journalSerializer;
        if (new File(_journalDirectory).exists())
            PrevalenceTest.delete(_journalDirectory);
        initializePrevayler();
    }

    public TransactionConnection createTestConnection() {
        return new PrevaylerTransactionConnection(prevayler);
    }

    public void reportResourcesUsed(PrintStream out) {
        int totalSize = 0;
        File[] files = new File(_journalDirectory).listFiles();
        for (int i = 0; i < files.length; i++) {
            totalSize += files[i].length();
        }
        out.println("Disk space used: " + totalSize);
    }

    public boolean isConsistent() throws Exception {
        int expectedResult = prevayler.prevalentSystem().hashCode();
        initializePrevayler(); // Will reload all transactions from the log
        // files.
        return prevayler.prevalentSystem().hashCode() == expectedResult;
    }

    private void initializePrevayler() throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        PrevaylerFactory<TransactionSystem> factory = new PrevaylerFactory<TransactionSystem>();
        factory.configurePrevalentSystem(new TransactionSystem());
        factory.configurePrevalenceDirectory(_journalDirectory);
        factory.configureJournalSerializer("journal", createJournalSerializer());
        factory.configureTransactionFiltering(false);
        prevayler = factory.create(); // No snapshot is generated by the test.
    }

    @SuppressWarnings("unchecked") private Serializer<Object> createJournalSerializer() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return (Serializer<Object>) Class.forName(_journalSerializer).newInstance();
    }

}
