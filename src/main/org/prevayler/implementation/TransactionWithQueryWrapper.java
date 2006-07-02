// Prevayler, The Free-Software Prevalence Layer
// Copyright 2001-2006 by Klaus Wuestefeld
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.
//
// Prevayler is a trademark of Klaus Wuestefeld.
// See the LICENSE file for license details.

package org.prevayler.implementation;

import org.prevayler.GenericTransaction;
import org.prevayler.PrevalenceContext;

import java.io.Serializable;

@SuppressWarnings("deprecation") public class TransactionWithQueryWrapper<S, R, E extends Exception> implements GenericTransaction<S, R, E>, Serializable {

    private static final long serialVersionUID = 1L;

    public org.prevayler.TransactionWithQuery<S, R, E> _transactionWithQuery;

    public TransactionWithQueryWrapper() {
    }

    public TransactionWithQueryWrapper(org.prevayler.TransactionWithQuery<S, R, E> transactionWithQuery) {
        _transactionWithQuery = transactionWithQuery;
    }

    public R executeOn(S prevalentSystem, PrevalenceContext prevalenceContext) throws E {
        return _transactionWithQuery.executeAndQuery(prevalentSystem, prevalenceContext.executionTime());
    }

}
